# Medo Progress & Notes Backup Format (`docs/progress-format.md`)

## 1. Overview
All user-generated state (answers, bookmarks, stars, flags, trap tags, personal notes, lesson highlights, time spent, and SM-2 spaced repetition state) is stored independently from question content and keyed by `siteQuestionId` (with fallback `contentHash`).

## 2. JSON Schema (`medo-progress-v1.json`)
```json
{
  "schemaVersion": 1,
  "exportedAt": 1762250000000,
  "appVersion": "2.0.0-next",
  "records": [
    {
      "siteQuestionId": "q_1001",
      "contentHash": "sha256_...",
      "lastSelectedOption": 2,
      "isCorrect": true,
      "attemptsCount": 3,
      "wrongCount": 1,
      "bookmarked": true,
      "starred": false,
      "flagged": false,
      "personalNote": "نکته کلیدی: در بیماران با CrCl < 30 دوز وانکومایسین تعدیل شود.",
      "highlightsJson": "[{\"start\":0,\"end\":28,\"color\":\"#FBBF24\"}]",
      "totalTimeSpentMs": 42500,
      "lastAnsweredAt": 1762249000000,
      "sm2": {
        "repetitions": 2,
        "easinessFactor": 2.5,
        "intervalDays": 6,
        "dueDateEpochDay": 20768,
        "suppressed": false
      }
    }
  ],
  "clozeCards": [
    {
      "cardId": "cloze_q_1001_0",
      "siteQuestionId": "q_1001",
      "clozeText": "خط اول درمان در شوک آنافیلاکسی تجویز فوری {{c1::اپی‌نفرین عضلانی (IM)}} است.",
      "answerText": "اپی‌نفرین عضلانی (IM)",
      "repetitions": 1,
      "easinessFactor": 2.6,
      "intervalDays": 1,
      "dueDateEpochDay": 20763,
      "suppressed": false
    }
  ]
}
```

## 3. Merge Strategy on Import
- Imported records are matched by `siteQuestionId` (or `contentHash` if `siteQuestionId` is absent).
- When merging with existing on-device progress, the record with the newer `lastAnsweredAt` timestamp wins for answer state, while `bookmarked`, `starred`, and `flagged` are OR-merged and non-empty `personalNote` strings are preserved.
