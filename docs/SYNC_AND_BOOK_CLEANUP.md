# Source files, reading time, and Gutenberg cleanup

Replacing a source file or cleaning an EPUB changes its content hash and clears its uploaded-file reference. Run **Full Sync** on that device to upload the new file. Run Full Sync on the other device to discover the revision, then download the updated book. The old downloaded file must no longer be offered as the current revision. A pending local replacement takes priority over an older cloud copy.

Reading time travels with **reading-progress sync**, including session checkpoints and their stable IDs. A full sync is needed initially to put a new book into the cloud library, but is not needed for subsequent reading-time updates. Session IDs prevent retries from counting time twice; independent sessions from different devices add together. Pausing or closing the reader saves its final checkpoint and attempts progress sync in the application scope. If the app process is killed or the network is unavailable, the saved checkpoint travels on a later sync.

The EPUB book menu has **Clean Up**. It removes only `div` or `section` elements explicitly identified as `pg-header` or `pg-footer`. It does not remove ordinary mentions of Gutenberg, illustrations, or unrecognized boilerplate. Archive resources and entry names are retained. Some editions can retain an empty introductory or closing page. PDFs and other formats are not supported.

Cleanup writes a new EPUB before updating the book record, retains the original source file on the device, preserves reading history and annotations, and clears the old locator and cached word/page estimates. Existing annotation links and the reading position may shift because the document changed. There is no automatic cleanup on download.
