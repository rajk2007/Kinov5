package com.rajk2007.kino.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.settings.extensions.PluginsViewModel
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RepoInstallerActivity : AppCompatActivity() {
    private val repos = listOf(
        Triple("Mega Repository", "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json", "megarepo"),
        Triple("CloudStream Providers", "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json", "cspr"),
        Triple("Phisher Repo", "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json", "phisherrepo"),
        Triple("Megix Repo", "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json", "csx")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_repo_installer)

        val row1 = findViewById<View>(R.id.repo_row_1)
        row1.findViewById<TextView>(R.id.repo_name).text = repos[0].first
        row1.findViewById<TextView>(R.id.repo_shortcode).text = repos[0].third
        
        val row2 = findViewById<View>(R.id.repo_row_2)
        row2.findViewById<TextView>(R.id.repo_name).text = repos[1].first
        row2.findViewById<TextView>(R.id.repo_shortcode).text = repos[1].third
        
        val row3 = findViewById<View>(R.id.repo_row_3)
        row3.findViewById<TextView>(R.id.repo_name).text = repos[2].first
        row3.findViewById<TextView>(R.id.repo_shortcode).text = repos[2].third
        
        val row4 = findViewById<View>(R.id.repo_row_4)
        row4.findViewById<TextView>(R.id.repo_name).text = repos[3].first
        row4.findViewById<TextView>(R.id.repo_shortcode).text = repos[3].third

        val startButton = findViewById<View>(R.id.start_watching_button)
        val allReadyText = findViewById<TextView>(R.id.all_ready_text)

        lifecycleScope.launch {
            for (i in repos.indices) {
                val repo = repos[i]
                val rowId = resources.getIdentifier("repo_row_${i + 1}", "id", packageName)
                val row = findViewById<View>(rowId)
                val progressBar = row.findViewById<View>(R.id.progress_bar)
                val checkmark = row.findViewById<View>(R.id.checkmark)

                // 1. Add repo
                // Use RepositoryData instead of Repository as RepositoryManager.addRepository expects RepositoryData
                RepositoryManager.addRepository(RepositoryData(repo.first, repo.second))
                
                // 2. Animate progress (simulated as actual download is background)
                progressBar.isVisible = true
                progressBar.animate().scaleX(1f).setDuration(1500).start()
                
                // 3. Trigger actual download
                try {
                    PluginsViewModel.downloadAll(this@RepoInstallerActivity, repo.second, null)
                } catch (e: Exception) {
                    // Fallback or log error
                }
                
                delay(1600)
                progressBar.isVisible = false
                checkmark.isVisible = true
            }

            allReadyText.isVisible = true
            startButton.isVisible = true
        }

        startButton.setOnClickListener {
            setKey("kino_repos_installed", true)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
