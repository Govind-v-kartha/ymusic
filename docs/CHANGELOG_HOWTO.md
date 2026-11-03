# How to Add a Release Changelog Entry

This guide explains how to document release changes in the in-app "What's new" dialog and "Changelog" settings viewer.

---

## 1. File Location
The bundled changelog resides at:
`commonUi/src/commonMain/composeResources/files/changelog.json`

A static compiled-in twin also resides in `commonUi/src/commonMain/kotlin/io/ymusic/ui/Changelog.kt` (`ChangelogStore.BUNDLED_JSON`).

---

## 2. JSON Entry Format
Before cutting each personal release tag:
1. Open `commonUi/src/commonMain/composeResources/files/changelog.json`.
2. Insert a new entry object at the top of the JSON array.
3. Increment the integer `id` (must be strictly greater than the previous entry's `id`).
4. Set `version` to the release tag (e.g., `v4.0.0-personal.2`).
5. Set `date` to the release date (`YYYY-MM-DD`).
6. Add bullet points to the `notes` list.

### Example:
```json
[
  {
    "id": 2,
    "version": "v4.0.0-personal.2",
    "date": "2026-11-10",
    "notes": [
      "Add feature X with improved performance",
      "Fix playback issue Y on Bluetooth disconnection"
    ]
  },
  {
    "id": 1,
    "version": "v4.0.0-personal.1",
    "date": "2026-11-03",
    "notes": [ ... ]
  }
]
```

---

## 3. Ground Rules for Notes
- **Tone:** Plain, direct, concise sentences.
- **Copy Constraints:**
  - No emojis.
  - No em dashes (`—`) or en dashes (`–`); use standard commas, colons, or parentheses.
- **Trigger Semantics:**
  - Existing users upgrading from a previous build will see the "What is new" pop-up once upon their first launch.
  - Fresh installs record the latest `id` without displaying the pop-up.
  - The full changelog history remains viewable anytime in Settings › About › Changelog.
