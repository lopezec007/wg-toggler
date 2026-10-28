package net.lopezec.wgtoggler

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * Periodic safety net for OEMs (Samsung in particular) that kill foreground services or
 * block their restart. Every ~15 minutes: if toggling is enabled but the watcher is not
 * running, start it again. Starting a foreground service from a job only succeeds when this
 * app is exempt from battery optimization, which the main screen asks for.
 */
class WatchdogJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val prefs = Prefs(this)
        if (prefs.enabled && !WifiWatcherService.running) {
            Log.w(TAG, "Watcher not running although enabled; restarting it")
            prefs.lastError = getString(R.string.error_restarted_by_watchdog)
            WifiWatcherService.start(this)
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val TAG = "WgToggler"
        private const val JOB_ID = 1

        fun schedule(context: Context) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, WatchdogJob::class.java))
                .setPeriodic(JobInfo.getMinPeriodMillis())
                .setPersisted(true)
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }
}
