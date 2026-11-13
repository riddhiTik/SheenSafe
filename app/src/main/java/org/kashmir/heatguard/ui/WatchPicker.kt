package org.kashmir.heatguard.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository

/**
 * Lets the user choose which paired watch to read from. Watch pairing itself happens in the
 * system companion app; this only picks among watches the Wearable Data Layer reports as
 * connected right now, and stores the choice (node id + name) in [AppRepository].
 */
object WatchPicker {

    sealed interface Result {
        data class Picked(val name: String) : Result
        object NoneFound : Result
        data class Error(val reason: String? = null, val unavailable: Boolean = false) : Result
        object Cancelled : Result
    }

    fun pick(context: Context, repository: AppRepository, onResult: (Result) -> Unit) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                when (nodes.size) {
                    0 -> onResult(Result.NoneFound)
                    1 -> select(repository, nodes.first(), nodes.first().displayName, onResult)
                    else -> showDialog(context, repository, nodes.toList(), onResult)
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("WatchPicker", "getConnectedNodes failed", e)
                val unavailable = (e as? com.google.android.gms.common.api.ApiException)?.statusCode == 17
                onResult(Result.Error("${e.javaClass.simpleName}: ${e.message}", unavailable))
            }
    }

    private fun showDialog(context: Context, repository: AppRepository, nodes: List<Node>, onResult: (Result) -> Unit) {
        // Two watches can share a display name; disambiguate with the end of the node id.
        val duplicated = nodes.groupBy { it.displayName }.filterValues { it.size > 1 }.keys
        val labels = nodes.map { if (it.displayName in duplicated) "${it.displayName} (${it.id.takeLast(4)})" else it.displayName }
        val current = nodes.indexOfFirst { it.id == repository.getSelectedWatchNodeId() }

        AlertDialog.Builder(context)
            .setTitle(R.string.watch_picker_title)
            .setSingleChoiceItems(labels.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                select(repository, nodes[which], labels[which], onResult)
            }
            .setNegativeButton(R.string.watch_picker_cancel) { _, _ -> onResult(Result.Cancelled) }
            .setOnCancelListener { onResult(Result.Cancelled) }
            .show()
    }

    private fun select(repository: AppRepository, node: Node, name: String, onResult: (Result) -> Unit) {
        repository.setSelectedWatch(node.id, name)
        onResult(Result.Picked(name))
    }
}
