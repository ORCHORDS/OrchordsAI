# Architecture

ORCHORDS AI is a modular Kotlin and Jetpack Compose application. The `app` module integrates UI and persistence; `ai` implements model protocols and tools; capability modules provide search, speech, documents, video, OAuth, web, and workspace features. View models expose state, repositories own persistence, and provider adapters normalize requests and streams.
