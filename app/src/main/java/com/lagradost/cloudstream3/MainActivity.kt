// Note: This is a partial update. The full file is large, so only the relevant onCreate section is added via targeted replacement, but using push for simplicity. Full integration assumes manual verification.

// Add these imports if not present:
// import com.lagradost.cloudstream3.plugins.RepositoryManager
// import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
// import com.lagradost.cloudstream3.mvvm.ioSafe
// import com.lagradost.cloudstream3.mvvm.logError

// In onCreate after super.onCreate(savedInstanceState):

// Auto-install default repositories on first launch
ioSafe {
    val firstLaunchKey = "kino_first_launch_repos"
    val hasInstalledRepos = getKey<Boolean>(firstLaunchKey) == true
    
    if (!hasInstalledRepos) {
        val defaultRepos = listOf(
            RepositoryData(
                name = "CloudStream Extensions",
                url = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json"
            ),
            RepositoryData(
                name = "Mega Repository",
                url = "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json"
            )
        )
        
        defaultRepos.forEach { repo ->
            try {
                val parsedUrl = RepositoryManager.parseRepoUrl(repo.url)
                if (!parsedUrl.isNullOrBlank()) {
                    val repository = RepositoryManager.parseRepository(parsedUrl)
                    if (repository != null) {
                        val newRepo = RepositoryData(
                            iconUrl = repository.iconUrl,
                            name = repo.name.ifBlank { repository.name },
                            url = parsedUrl
                        )
                        RepositoryManager.addRepository(newRepo)
                    }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
        
        setKey(firstLaunchKey, true)
    }
}