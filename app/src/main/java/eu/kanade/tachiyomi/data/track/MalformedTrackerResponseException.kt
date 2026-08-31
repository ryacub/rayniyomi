package eu.kanade.tachiyomi.data.track

class MalformedTrackerResponseException(
    trackerName: String,
    fieldName: String,
) : IllegalStateException(
    "$trackerName returned an invalid response: $fieldName is absent",
)
