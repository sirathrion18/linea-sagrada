package ar.nacho.lineasagrada

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ar.nacho.lineasagrada.ui.MainActivity
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import java.util.concurrent.TimeUnit

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL, "Expedientes cerrados", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "Aviso cuando termina un evento y hay fotos para revisar"
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(false)
        .build()

    companion object {
        const val CHANNEL = "cierres"
    }
}

object Perms {
    private fun ok(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun request(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()

    fun hasMedia(ctx: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 34 -> ok(ctx, Manifest.permission.READ_MEDIA_IMAGES) ||
            ok(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> ok(ctx, Manifest.permission.READ_MEDIA_IMAGES)
        else -> ok(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun partial(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 34 &&
        !ok(ctx, Manifest.permission.READ_MEDIA_IMAGES) &&
        ok(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    fun location(ctx: Context): Boolean = ok(ctx, Manifest.permission.ACCESS_MEDIA_LOCATION)
}

/** Una vez por día: sigue el análisis y avisa si se cerró un evento con cosas para limpiar. */
class CierreWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Perms.hasMedia(ctx)) return Result.success()
        val repo = Repo.get(ctx)
        repo.refresh(budgetMs = 8 * 60_000L)
        val lib = repo.library.value ?: return Result.success()
        val already = synchronized(repo.edits) { HashSet(repo.edits.notified) }
        for (ev in lib.closed) {
            if (ev.id in already) continue
            val n = lib.eventCleanup(ev).sumOf { it.items.size }
            notify(ctx, ev, n)
            repo.markNotified(ev.id)
        }
        return Result.success()
    }

    private fun notify(ctx: Context, ev: Event, n: Int) {
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_EVENT, ev.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, ev.id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val note = NotificationCompat.Builder(ctx, App.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFFFF6A00.toInt())
            .setContentTitle("Expediente cerrado: ${ev.name}")
            .setContentText("$n archivos para revisar antes de archivarlo")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ev.id.hashCode(), note)
        } catch (e: SecurityException) {
        }
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<CierreWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("cierre", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
