package com.steady.habittracker.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens the Grok Android app (or a share fallback) with a crafted plain-text message.
 * Always copies the text to the clipboard so the user can paste and edit in Grok.
 */
object GrokLauncher {

    /** Known package ids for the official Grok app. */
    val GROK_PACKAGES: List<String> = listOf(
        "ai.x.grok",
        "com.xai.grok"
    )

    data class Result(
        val openedGrok: Boolean,
        val usedShareSheet: Boolean,
        val copied: Boolean,
        val message: String
    )

    /**
     * Copy [text] to clipboard, then try to send it to Grok via ACTION_SEND.
     * Falls back to the system share sheet, then Play Store / web if needed.
     */
    fun sendToGrok(context: Context, text: String): Result {
        val body = text.trim().ifBlank { return Result(false, false, false, text) }
        val copied = copyToClipboard(context, body)

        // 1) Prefer installed Grok package with ACTION_SEND (text/plain)
        for (pkg in GROK_PACKAGES) {
            if (!isPackageInstalled(context, pkg)) continue
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
                putExtra(Intent.EXTRA_SUBJECT, "Steady → Grok")
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (send.resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(send)
                    Toast.makeText(
                        context,
                        if (copied) "Opened Grok · message also copied" else "Opened Grok",
                        Toast.LENGTH_SHORT
                    ).show()
                    return Result(openedGrok = true, usedShareSheet = false, copied = copied, message = body)
                } catch (_: ActivityNotFoundException) {
                    // try next package
                } catch (_: Exception) {
                    // try next package
                }
            }
            // Launch main activity + rely on clipboard paste
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(launch)
                    Toast.makeText(
                        context,
                        "Opened Grok · paste the message (already copied)",
                        Toast.LENGTH_LONG
                    ).show()
                    return Result(openedGrok = true, usedShareSheet = false, copied = copied, message = body)
                } catch (_: Exception) {
                    // continue
                }
            }
        }

        // 2) Generic share sheet (user can pick Grok if it appears)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_SUBJECT, "Steady → Grok")
        }
        try {
            val chooser = Intent.createChooser(share, "Send to Grok").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Toast.makeText(
                context,
                if (copied) "Choose Grok · message also copied" else "Choose an app",
                Toast.LENGTH_SHORT
            ).show()
            return Result(openedGrok = false, usedShareSheet = true, copied = copied, message = body)
        } catch (_: Exception) {
            // fall through
        }

        // 3) Offer install / open web
        openGrokInstallOrWeb(context)
        Toast.makeText(
            context,
            if (copied) "Message copied · install Grok to chat" else "Could not open Grok",
            Toast.LENGTH_LONG
        ).show()
        return Result(openedGrok = false, usedShareSheet = false, copied = copied, message = body)
    }

    fun copyOnly(context: Context, text: String): Boolean {
        val ok = copyToClipboard(context, text.trim())
        Toast.makeText(
            context,
            if (ok) "Message copied" else "Could not copy",
            Toast.LENGTH_SHORT
        ).show()
        return ok
    }

    fun isGrokInstalled(context: Context): Boolean =
        GROK_PACKAGES.any { isPackageInstalled(context, it) }

    private fun copyToClipboard(context: Context, text: String): Boolean {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Steady → Grok", text))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openGrokInstallOrWeb(context: Context) {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=ai.x.grok")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
            return
        } catch (_: Exception) {
            // fall through
        }
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=ai.x.grok")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://grok.com"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                // give up
            }
        }
    }
}
