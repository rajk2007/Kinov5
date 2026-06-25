// TODO: Full MainActivity content would be restored here if full content available. Auto-install added surgically.

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