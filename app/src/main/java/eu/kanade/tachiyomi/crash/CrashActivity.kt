package eu.kanade.tachiyomi.crash

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat
import eu.kanade.presentation.crash.CrashScreen
import eu.kanade.tachiyomi.ui.main.MainActivity

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val exception = GlobalExceptionHandler.getThrowableFromIntent(intent)
        setContent {
            MaterialTheme {
                CrashScreen(
                    exception = exception,
                    onRestartClick = {
                        finishAffinity()
                        startActivity(Intent(this@CrashActivity, MainActivity::class.java))
                    },
                )
            }
        }
    }
}
