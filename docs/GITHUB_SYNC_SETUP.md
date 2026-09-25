# Set up GitHub sync

Vayana can sync your library through a GitHub repository that you control. No Vayana account or proprietary cloud service is required.

This guide creates a dedicated private repository, gives Vayana access only to that repository, performs the first upload, and connects additional devices safely.

## Before you begin

You need:

- A GitHub account
- Vayana installed on each Android device
- A strong sync passphrase that you can enter on every device
- About 10 minutes for the initial setup

> [!IMPORTANT]
> Use a **private** repository. Vayana encrypts EPUB and cover files before upload, but sync metadata—including book titles, reading positions, annotations, shelves, vocabulary, and reading history—is stored as readable JSON.

> [!WARNING]
> Your GitHub token is a credential. Never post it in an issue, screenshot, log, or public repository. Vayana keeps the token on the device; an exported sync-setup file contains it and is therefore encrypted with your sync passphrase.

## 1. Create a private sync repository

1. Sign in to [GitHub](https://github.com/).
2. In the upper-right corner, select **+**, then **New repository**.
3. Choose your personal account as the **Owner**. An organization also works if its policies allow fine-grained tokens and direct writes.
4. Enter a repository name, such as `vayana-sync`.
5. Select **Private**.
6. Turn on **Add a README file**. This creates the initial `main` branch that Vayana will use.
7. Select **Create repository**.

Write down these three values:

| Vayana field | Example | What to enter |
| --- | --- | --- |
| **Owner** | `octocat` | Your GitHub username or organization name |
| **Repository** | `vayana-sync` | The repository name only, without the owner or `.git` |
| **Branch** | `main` | The branch Vayana may write to |

GitHub also provides an official [repository creation guide](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository).

## 2. Create a fine-grained access token

A fine-grained personal access token lets Vayana read and write only the sync repository instead of granting access to all of your repositories.

1. On GitHub, open your profile menu and select **Settings**.
2. Open **Developer settings**.
3. Select **Personal access tokens** → **Fine-grained tokens**.
4. Select **Generate new token**.
5. Enter a recognizable name, such as `Vayana on Phone`.
6. Choose an expiration date. When the token expires, create a replacement and update it on every device.
7. Set **Resource owner** to the account that owns the sync repository.
8. Under **Repository access**, choose **Only select repositories**, then select `vayana-sync`.
9. Under **Repository permissions**, set **Contents** to **Read and write**. No other write permission is needed.
10. Select **Generate token**, then copy it immediately and keep it private.

GitHub documents both [fine-grained token creation](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) and the [Contents permission required to create or update files](https://docs.github.com/en/rest/repos/contents#create-or-update-file-contents).

> [!NOTE]
> An organization may require an administrator to approve the token. If approval is pending, Vayana cannot reach the private repository yet. A repository under your personal account is usually the simplest setup.

## 3. Configure the first device

1. Open Vayana.
2. Open **Settings**.
3. Find **Sync — GitHub sync, device name, and encryption**.
4. Set **Device name** to a short, unique name, such as `Pixel 9`, `Boox Palma`, or `Tablet`. This name appears in sync history and conflict messages.
5. Enter the **GitHub owner**, **Repository**, and **Branch** from step 1.
6. Paste the fine-grained token into **GitHub token**.
7. Enter a strong **Sync passphrase**.
8. Under **Test GitHub connection**, select **Test connection**.
9. Confirm that Vayana reports either:
   - **GitHub connection works. Existing sync metadata is readable**, or
   - **No sync snapshot exists yet, so initial sync can create one**.
10. Turn on **GitHub sync**.
11. Optionally set **Sync while reading**. The default uploads reading progress every 3 page turns; enter `0` to disable automatic progress sync.

The passphrase encrypts uploaded EPUB and cover files and the exported setup file. It is not sent to GitHub. Every connected device must use exactly the same passphrase.

> [!CAUTION]
> Vayana cannot recover a lost passphrase. Keep it in a trusted password manager. Changing it later will prevent existing encrypted book and cover files from being opened by devices using the new passphrase.

## 4. Perform the first full sync

Complete the first upload on one device before configuring another device.

1. Return to the **Books** screen.
2. Select the circular **Sync** icon in the top app bar.
3. Select **Full sync**.
4. When Vayana asks **Start initial GitHub sync?**, verify that this is the new repository and select **Yes, upload**.
5. Keep Vayana open while it reads the cloud library, uploads books and covers, and saves the snapshot.
6. Wait for the sync progress to show **Complete**.

The repository will now contain a `vayana/` directory. Let Vayana manage the files in that directory; editing or renaming them manually can break sync.

## 5. Connect another device

You can enter the settings manually, but Vayana's encrypted setup transfer is faster and avoids token-entry mistakes.

### On the first device

1. Open **Settings** → **Sync**.
2. Under **Transfer sync setup**, select **Export**.
3. Save the encrypted setup file somewhere you can move securely to the other device.

### On the additional device

1. Install and open Vayana.
2. Open **Settings** → **Sync**.
3. Enter the same **Sync passphrase** used on the first device. This must be done before importing.
4. Under **Transfer sync setup**, select **Import**, then choose the exported setup file.
5. Change **Device name** to a unique name. Importing copies the original device name, and leaving it unchanged would make sync history and conflicts ambiguous.
6. Select **Test connection**.
7. Make sure **GitHub sync** is on.
8. Return to **Books**, select the **Sync** icon, then **Full sync**.
9. Wait for **Complete** before reading or making library changes on both devices.
10. Delete the transferred setup file after every device has imported it. It is encrypted, but it still contains the GitHub token.

Repeat these steps for each additional device, giving every device a different name.

## Everyday sync behavior

- **Full sync** merges the library, books, covers, annotations, shelves, vocabulary, reading sessions, settings, deletions, and reading progress.
- **Reading progress only** exchanges the latest reading positions without running the full library workflow.
- **Sync while reading** performs lightweight progress sync after the configured number of page turns.
- If two devices report different positions, the conflict dialog shows the sync time and device name so you can choose the correct position.
- Permanently deleted encrypted files may remain in older Git commits unless the repository history is rewritten.

For the cleanest results, let one device finish syncing before starting a sync on another device.

## Troubleshooting

| What you see | What to check |
| --- | --- |
| **No sync snapshot exists yet** | This is expected for a new repository. Run the first full sync from the device whose library should initialize the cloud copy. |
| **401 / bad credentials** | The token is incorrect, expired, or revoked. Create a replacement token, update every device, and test again. |
| **403 / forbidden** | Confirm the token has **Contents: Read and write**, organization approval is complete, and branch rules allow Vayana to write directly. |
| **404 / not found** | Check the owner, repository name, branch, selected repository access, and whether the token can access a private repository. |
| **Upload rejected or protected branch error** | Use a dedicated branch that does not require pull requests, reviews, or signed commits for every change. |
| **Import or decryption fails** | Enter the exact passphrase used to export the setup and encrypt the existing cloud assets. Passphrases are case-sensitive. |
| **Both devices show the same device name** | Rename each device in **Settings** before the next sync. |
| **A conflict dialog appears** | Compare the displayed position, sync time, and device name, then choose the version you want to keep. |

## Security checklist

- Keep the sync repository **private**.
- Give the fine-grained token access to only the sync repository.
- Grant only **Contents: Read and write**.
- Use a token expiration date and rotate the token when needed.
- Store the sync passphrase in a password manager.
- Use a unique device name on every device.
- Delete exported setup files after import.
- Never share the token, passphrase, or repository contents in public bug reports.

[Return to the Vayana README](../README.md)
