package org.kashmir.heatguard.alerts

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import org.kashmir.heatguard.HeatGuardApplication
import org.kashmir.heatguard.R
import org.kashmir.heatguard.core.escalation.EscalationAction
import org.kashmir.heatguard.core.escalation.EscalationPlan
import org.kashmir.heatguard.core.model.RiskDecision
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.ui.EmergencyAlertActivity
import org.kashmir.heatguard.ui.MainActivity

class EscalationManager(
    private val context: Context,
    private val repository: AppRepository
) {
    /**
     * Routine (NOTIFY_USER) risk updates never hit the system tray — they're delivered as an
     * in-app banner only while a screen is registered via [HeatGuardApplication.inAppAlertListener],
     * and always recorded to notification history (see bell icon) regardless of whether the app
     * is open. High-risk/emergency delivery (dial prompt, full-screen alert) is unaffected.
     */
    fun showRiskNotification(decision: RiskDecision) {
        val app = context.applicationContext as HeatGuardApplication
        app.inAppAlertListener?.invoke(decision)
    }

    fun dialEmergencyContact() {
        val phone = repository.getPrimaryPhone()
        if (phone.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openSmsForContact(phone: String) {
        if (phone.isBlank()) return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}"))
        intent.putExtra("sms_body", "Emergency: I need help immediately. Please call me.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Texts every non-primary contact (the primary contact is called instead, via [dialEmergencyContact]). */
    fun smsNonPrimaryContacts() {
        repository.getNonPrimaryPhones().forEach { phone -> openSmsForContact(phone) }
    }

    fun execute(plan: EscalationPlan, decision: RiskDecision) {
        if (plan.actions.isEmpty()) return
        for (action in plan.actions) {
            when (action) {
                EscalationAction.NOTIFY_USER -> showRiskNotification(decision)
                EscalationAction.SUGGEST_DIAL_CONTACT -> {
                    val now = System.currentTimeMillis()
                    if (!repository.isEmergencyAlertSuppressed(now)) {
                        launchEmergencyAlert()
                    } else {
                        dialEmergencyContact()
                    }
                }
                EscalationAction.ALERT_VOLUNTEER,
                EscalationAction.LOG_EMERGENCY_EVENT -> {
                    // Handled by the UI layer or external systems
                }
            }
        }
    }

    private fun launchEmergencyAlert() {
        val intent = Intent(context, EmergencyAlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_EMERGENCY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, NotificationHelper.CHANNEL_ALERTS)
        } else {
            android.app.Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.emergency_notification_title))
            .setContentText(context.getString(R.string.emergency_notification_body))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setCategory(android.app.Notification.CATEGORY_ALARM)
            .setPriority(android.app.Notification.PRIORITY_MAX)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_EMERGENCY, notification)
    }

    companion object {
        private const val REQUEST_CODE_EMERGENCY = 101
        private const val NOTIFICATION_ID_EMERGENCY = 201
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
