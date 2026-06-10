package org.kashmir.heatguard.alerts

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import org.kashmir.heatguard.R
import org.kashmir.heatguard.core.escalation.EscalationAction
import org.kashmir.heatguard.core.escalation.EscalationPlan
import org.kashmir.heatguard.core.model.RiskDecision
import org.kashmir.heatguard.core.model.RiskLevel
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.ui.MainActivity

class EscalationManager(
    private val context: Context,
    private val repository: AppRepository
) {
    fun showRiskNotification(decision: RiskDecision) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, NotificationHelper.CHANNEL_ALERTS)
        } else {
            android.app.Notification.Builder(context)
        }
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.heat_alert_title, decision.level.name))
            .setContentText(decision.recommendedAction)
            .setContentIntent(pendingIntent)
            .setAutoCancel(decision.level != RiskLevel.EMERGENCY)
            .build()
        manager.notify(200, notification)
    }

    fun dialEmergencyContact() {
        val phone = repository.getEmergencyContactPhone()
        if (phone.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun execute(plan: EscalationPlan, decision: RiskDecision) {
        if (plan.actions.isEmpty()) return
        for (action in plan.actions) {
            when (action) {
                EscalationAction.NOTIFY_USER -> showRiskNotification(decision)
                EscalationAction.SUGGEST_DIAL_CONTACT -> dialEmergencyContact()
                EscalationAction.ALERT_VOLUNTEER,
                EscalationAction.LOG_EMERGENCY_EVENT -> {
                    // Handled by the UI layer or external systems
                }
            }
        }
    }

    fun shareEmergencyMessage(decision: RiskDecision) {
        val contactName = repository.getEmergencyContactName()
        val message = buildString {
            append(context.getString(R.string.share_emergency_prefix))
            append('\n')
            append(context.getString(R.string.status_label))
            append(": ")
            append(decision.level.name)
            append('\n')
            append(decision.recommendedAction)
            if (!TextUtils.isEmpty(contactName)) {
                append('\n')
                append(context.getString(R.string.contact_label))
                append(": ")
                append(contactName)
            }
        }
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_alert)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
