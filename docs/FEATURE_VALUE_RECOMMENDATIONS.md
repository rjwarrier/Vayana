# Value-for-money feature recommendations

For Vayana, prioritize daily reading convenience and making saved knowledge useful.
Here, **value for money** means user benefit relative to development and ongoing
maintenance effort.

## Recommended features

| Priority | Feature | Reader benefit | Relative effort |
|---|---|---|---|
| **1** | **Reading history + “Back to where I was”** | Explore a footnote, search result, or chapter without losing the original position. | Low–medium |
| **2** | **Markdown export for highlights** | Export a book’s highlights with title, author, chapters, and personal notes. Builds on existing plain-text sharing. | Low |
| **3** | **Recently deleted books** | Undo accidental deletion and restore books with their annotations. The database already uses soft deletion. | Low |
| **4** | **Personal shelves + “Read next” queue** | Organize books into topics and choose the next read. Extends existing author/series grouping. | Medium |
| **5** | **Vocabulary review cards** | Turn dictionary lookups into saved words with definitions and the original sentence. Start with “Review five words.” | Medium |
| **6** | **Book and notes side by side** | Read while viewing or editing notes on a tablet. Makes the larger screen substantially more useful. | Medium |
| **7** | **Per-book reading preferences** | Remember different typography and margins for each book, with a reset-to-default option. | Low–medium |

These are development estimates, not measured demand. As benchmarks,
[KOReader supports structured highlight exports](https://koreader.rocks/user_guide/),
while [ReadEra provides personal collections and reading lists](https://readera.org/).

## Suggested next release

Build **#1, #2, and #3**:

1. Reading history and return to the original position.
2. Markdown export for highlights.
3. Recently deleted books with restoration.

They offer frequent, tangible benefits, reuse existing foundations, and need no
server infrastructure.

## Larger follow-up

**Read-aloud with offline voices and a sleep timer** has strong potential. Treat it
as a separate milestone: reliable chapter traversal, background playback, and
audio controls make it more substantial than a quick addition.
