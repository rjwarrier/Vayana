<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="core/resources/src/main/res/drawable-nodpi/vayana_share_dark_reader.webp">
    <source media="(prefers-color-scheme: light)" srcset="core/resources/src/main/res/drawable-nodpi/vayana_share_light.webp">
    <img alt="Vayana : une liseuse pour Android. Lisez sans interruption." src="core/resources/src/main/res/drawable-nodpi/vayana_share_light.webp" width="820">
  </picture>

  <h1>Vayana</h1>

  <p><strong>Lisez sans interruption.</strong></p>
  <p>Un lecteur <a href="docs/USER_GUIDE.fr.md#reading-epub-books">EPUB</a> et <a href="docs/USER_GUIDE.fr.md#reading-pdf-books">PDF</a> pour Android, paisible et privilégiant le stockage local, conçu pour les écrans classiques et les <a href="docs/USER_GUIDE.fr.md#e-ink-and-accessibility">appareils E‑Ink</a>.</p>

  <p>
    <a href="https://github.com/rjwarrier/Vayana/releases/latest"><strong>Télécharger la dernière version</strong></a> ·
    <a href="https://ranjithj.in/vayana/"><strong>Site de Vayana</strong></a> ·
    <a href="https://github.com/rjwarrier/Vayana/releases/tag/v0.87">Notes de version</a> ·
    <a href="docs/USER_GUIDE.fr.md">Guide d’utilisation</a> ·
    <a href="docs/FEATURES.md">Documentation des fonctionnalités</a> ·
    <a href="docs/GITHUB_SYNC_SETUP.md">Configurer la synchronisation GitHub</a>
  </p>

  <p>
    <img alt="Version v0.87" src="https://img.shields.io/badge/release-v0.87-00695c?style=flat-square">
    <img alt="Android 8.0 ou version ultérieure" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
    <img alt="Material 3" src="https://img.shields.io/badge/Material-3-6750A4?style=flat-square&logo=materialdesign&logoColor=white">
  </p>

  <p><a href="https://www.buymeacoffee.com/ranjithj"><img alt="Offrez-moi un café" src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=&slug=ranjithj&button_colour=FFDD00&font_colour=000000&font_family=Bree&outline_colour=000000&coffee_colour=ffffff" height="44"></a></p>
</div>

[English](README.md) · [Español](README.es.md) · [Português (Brasil)](README.pt.md) · **Français** · [Deutsch](README.de.md)

<a id="why-vayana"></a>

## Pourquoi Vayana ?

La plupart des applications de lecture se limitent à afficher un livre. Vayana considère la lecture comme une pratique cohérente : organisez ce que vous souhaitez lire, restez plongé dans le texte, conservez les passages importants, apprenez des mots nouveaux, retrouvez ce qui vous a marqué et emportez votre progression en toute sécurité d’un appareil à l’autre.

Vayana le fait sans imposer de compte Vayana ni afficher de publicités pendant la lecture. Votre bibliothèque est locale par défaut. Les fonctionnalités en ligne facultatives, comme l’[enrichissement Goodreads](docs/USER_GUIDE.fr.md#book-details-and-reading-status) et la [synchronisation GitHub que vous gérez vous-même](docs/USER_GUIDE.fr.md#github-sync), restent sous votre contrôle.

<a id="latest-source-updates"></a>

## Dernières évolutions du code source

Le code actuel comprend une [application compagnon Wear OS](docs/WEAR_OS.md) pour la lecture de livres papier : chronomètres hors ligne, suivi des pages, commandes partagées entre téléphone et montre, objectifs de lecture, une tuile et une complication de cadran. Les corrections récentes de synchronisation préservent le temps de lecture entre appareils et gèrent les retours en arrière de l’horloge en toute sécurité. Consultez les [changements depuis la v0.87](docs/releases/UNRELEASED.md).

La version publiée **v0.87** contient actuellement uniquement l’APK pour téléphone. Compilez les applications actuelles pour téléphone et montre afin d’utiliser les fonctionnalités du compagnon ; consultez la [configuration Wear OS](docs/WEAR_OS.md#install-and-build). L’application de la montre enregistre les séances de lecture de livres papier ; elle n’affiche pas de livres EPUB ou PDF.

<a id="highlights-in-087"></a>

## Points forts de la version 0.87

- Lisez des livres [**EPUB**](docs/USER_GUIDE.fr.md#reading-epub-books) et [**PDF**](docs/USER_GUIDE.fr.md#reading-pdf-books), avec plans PDF, sélection de texte, annotations, dictionnaire, zoom et réglages d’affichage par livre.
- Recherchez et téléchargez des livres du domaine public via [**Project Gutenberg**](docs/USER_GUIDE.fr.md#download-free-books-from-project-gutenberg), ou connectez des [**catalogues OPDS**](docs/USER_GUIDE.fr.md#connect-an-opds-catalog) tels que Calibre, Calibre-Web et Standard Ebooks.
- Conservez une seule bibliothèque pour les livres numériques, [sans fichier numérique, papier et empruntés](docs/USER_GUIDE.fr.md#offline-physical-borrowed-and-other-books), avec rappels de retour et miroir facultatif en lecture seule de [**Home Library**](docs/USER_GUIDE.fr.md#home-library-mirror).
- [Consultez et traduisez des mots ou expressions](docs/USER_GUIDE.fr.md#dictionary-translation-and-vocabulary) dans la langue du livre grâce au dictionnaire hors ligne, à Wiktionary, à Wikipedia ou aux outils de traduction.
- Reprenez la lecture, contrôlez la lecture à voix haute et consultez le temps de lecture des sept derniers jours grâce aux [**widgets et raccourcis de l’écran d’accueil**](docs/USER_GUIDE.fr.md#widgets-and-app-shortcuts) configurables.
- Enregistrez les plantages et les échecs de synchronisation dans l’écran [**Diagnostic**](docs/USER_GUIDE.fr.md#diagnostics-and-support), conçu dans le respect de la vie privée, puis partagez un rapport expurgé des données sensibles avec le développeur.
- Utilisez Vayana en [anglais, espagnol, portugais, russe, allemand, français, italien, malayalam ou tamoul](docs/USER_GUIDE.fr.md#settings-and-languages).

<a id="screenshots"></a>

## Captures d’écran

<table>
  <tr>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/01-notes-and-highlights.png" alt="Notes et surlignages avec filtres, recherche et annotations de la communauté" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.fr.md#highlights-notes-bookmarks-and-quotes"><strong>Surlignages et notes</strong></a> : recherchez, filtrez, modifiez, partagez et distinguez vos annotations des citations de la communauté.</sub>
    </td>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/02-notes-library.png" alt="Bibliothèque de notes regroupées par livre" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.fr.md#notes-library"><strong>Bibliothèque de notes</strong></a> : parcourez les annotations par livre et voyez les nombres de chapitres et de notes d’un coup d’œil.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/03-book-library.png" alt="Bibliothèque Vayana avec carte de lecture en cours et grille de couvertures" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.fr.md#managing-the-library"><strong>Bibliothèque</strong></a> : continuez à lire, recherchez et filtrez une collection organisée autour des couvertures.</sub>
    </td>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/04-book-details.png" alt="Détails d’un livre avec métadonnées Goodreads, progression et statistiques de lecture" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.fr.md#book-details-and-reading-status"><strong>Détails du livre</strong></a> : métadonnées, citations de la communauté, note personnelle, progression et statistiques de lecture au même endroit.</sub>
    </td>
  </tr>
</table>

<a id="what-makes-it-different"></a>

## Ce qui fait la différence

<a id="a-real-eink-mode"></a>

### [Un véritable mode E‑Ink](docs/USER_GUIDE.fr.md#e-ink-and-accessibility)

E‑Ink est un profil d’affichage à part entière, plutôt qu’un filtre de couleur :

- Les mouvements continus sont supprimés ; la ligne ondulée de progression de lecture devient statique.
- L’animation de changement de page est désactivée et l’horloge du lecteur n’est actualisée qu’aux changements de page.
- Choisissez **Monochrome** ou **Couleur** dans **Paramètres → Apparence → Palette E-ink**, ou pendant la configuration initiale. Monochrome reste le choix par défaut : les couvertures utilisent des niveaux de gris très contrastés et les surlignages deviennent des marques noires différenciées par leur forme. Couleur préserve les couvertures, illustrations, surlignages et accents du thème sur les écrans E-Ink couleur.
- Les deux palettes conservent le comportement E-Ink concernant les mouvements, la pagination et le rafraîchissement ; la couleur ne réactive pas les animations.
- E-Ink couleur utilise une palette dédiée très contrastée : surfaces neutres, texte noir/blanc, contours pleins et accents bleu-vert, bleus et bordeaux. Les modes clair et sombre sont pris en charge ; les couleurs du fond d’écran et les variantes de surface OLED sont ignorées dans ce profil. Les couvertures et les thèmes de page choisis restent inchangés. Les contrôles numériques de contraste ne remplacent pas des essais sur l’écran avec son éclairage frontal et ses réglages de rafraîchissement.
- Les touches physiques de pagination tournent les pages du lecteur et permettent d’avancer dans les écrans longs de l’application.
- Bibliothèque, Notes, Recherche, Paramètres, Étagères et Statistiques disposent de commandes pour avancer d’un écran à la fois.
- Les rafraîchissements de nettoyage du lecteur peuvent intervenir après un nombre choisi de pages et aux transitions entre chapitres ou menus.
- Les réglages typographiques comprennent un texte plus épais, l’alignement et la césure.

<a id="reading-that-becomes-learning"></a>

### [Une lecture qui devient apprentissage](docs/USER_GUIDE.fr.md#dictionary-translation-and-vocabulary)

- Touchez deux fois un mot pour ouvrir le dictionnaire hors ligne intégré ; les mots absents peuvent être recherchés dans Wiktionary ou Wikipedia d’un simple toucher.
- Enregistrez directement les mots consultés dans un ensemble de révision espacée.
- Découvrez les mots inhabituels du chapitre en cours et leurs définitions.
- Exportez le vocabulaire en CSV compatible avec Anki ou en Markdown lisible.
- Révisez vos surlignages à intervalles espacés : **Revoir bientôt**, **Compris** ou **Bien connu**.
- Après une absence, retrouvez un récapitulatif avec un surlignage récent et du vocabulaire à réviser.

<a id="read-aloud-that-follows-the-book"></a>

### [Une lecture à voix haute qui suit le livre](docs/USER_GUIDE.fr.md#read-aloud)

- La synthèse vocale Android lit phrase par phrase et passe d’un chapitre à l’autre.
- Les mots ou phrases prononcés sont marqués directement dans le texte.
- Lancez la lecture depuis un passage sélectionné ; touchez le lecteur n’importe où pour mettre en pause.
- Choisissez le moteur TTS installé, la voix, la vitesse et la hauteur de voix.
- Utilisez une minuterie d’arrêt, la gestion de la priorité audio, les commandes de casque/Bluetooth, les commandes de l’écran verrouillé et les actions des notifications.

<a id="sync-without-a-proprietary-service"></a>

### [Une synchronisation sans service propriétaire](docs/USER_GUIDE.fr.md#github-sync)

Utilisez un dépôt GitHub que vous contrôlez pour synchroniser :

- Position de lecture, séances et dates de lecture
- Livres, couvertures et métadonnées de la bibliothèque
- Surlignages, notes, étagères et Prochaines lectures
- Vocabulaire et paramètres portables
- Suppressions et restaurations entre appareils

Les fichiers des livres et des couvertures sont chiffrés avec AES-GCM à l’aide de votre phrase secrète avant leur envoi. La synchronisation légère de progression peut s’effectuer pendant la lecture, et les messages de conflit identifient la position la plus récente avec l’heure de synchronisation et le nom de l’appareil. La configuration de synchronisation peut elle-même être exportée dans un fichier de transfert chiffré pour un autre appareil.

Suivez le [guide pas à pas de configuration GitHub](docs/GITHUB_SYNC_SETUP.md) pour créer un dépôt privé, configurer un jeton aux permissions minimales, connecter le premier appareil et en ajouter d’autres en toute sécurité.

<a id="features"></a>

## Fonctionnalités

| Domaine | Points forts |
| --- | --- |
| [**Bibliothèque**](docs/USER_GUIDE.fr.md#managing-the-library) | Import EPUB/PDF, analyse de dossiers, **Ouvrir avec** d’Android, détection des doublons, vues grille/liste, filtres, tri, lecture en cours, Prochaines lectures, étagères, dossiers de séries et Suppressions récentes |
| [**Livres sans fichier numérique et papier**](docs/USER_GUIDE.fr.md#offline-physical-borrowed-and-other-books) | Suivi sans fichier local, progression et chronomètres, statut possédé/emprunté, dates de retour et rappels ; miroir facultatif du catalogue partagé par Home Library |
| [**Détails du livre**](docs/USER_GUIDE.fr.md#book-details-and-reading-status) | Métadonnées, séries et étiquettes modifiables, évaluations, dates de lecture, temps passé, couvertures personnalisées, remplacement du fichier source, partage de fichiers et cartes visuelles de lecture |
| [**Goodreads**](docs/USER_GUIDE.fr.md#book-details-and-reading-status) | Aperçu et import des séries, genres, description, couverture, année de publication, évaluation et citations populaires ; recours au navigateur si l’accès direct est bloqué |
| [**Découverte de livres**](docs/USER_GUIDE.fr.md#adding-books) | Exploration et téléchargement depuis Project Gutenberg par sujet ou langue, et connexion aux catalogues OPDS dont Calibre, Calibre-Web et Standard Ebooks |
| [**Lecteur EPUB**](docs/USER_GUIDE.fr.md#reading-epub-books) | Navigation par sommaire, position mémorisée, préférences par livre, polices importées, thèmes, marges, en-têtes/pieds de page, styles de l’éditeur, zones tactiles, touches de volume, plein écran et deux colonnes en paysage |
| [**Lecteur PDF**](docs/USER_GUIDE.fr.md#reading-pdf-books) | Navigation par pages et plan, ajustement/zoom, sélection de texte, dictionnaire, surlignages, soulignements, notes, signets, copie et cartes de citations pour PDF avec texte |
| [**Typographie**](docs/USER_GUIDE.fr.md#typography-and-page-appearance) | Famille et taille de police, hauteur de ligne, polices personnalisées, alignement, césure, texte plus épais et lecture bionique facultative |
| [**Annotations**](docs/USER_GUIDE.fr.md#highlights-notes-bookmarks-and-quotes) | Surlignages, soulignements, signets, notes, étiquettes d’annotation, fusion de surlignages superposés, fenêtres de notes de bas de page et retour direct au passage |
| [**Notes et citations**](docs/USER_GUIDE.fr.md#notes-library) | Vues Notes globales et par livre, import Kindle `My Clippings.txt`, export Markdown, import de citations Goodreads et images de cartes de citations à partager |
| [**Dictionnaire et traduction**](docs/USER_GUIDE.fr.md#dictionary-translation-and-vocabulary) | Consultation hors ligne selon la langue du livre, recherche d’expressions, recours à Wiktionary/Wikipedia, traduction, vocabulaire enregistré, mots du chapitre, révision espacée, suivi des mots connus et export Anki/Markdown |
| [**Lecture à voix haute**](docs/USER_GUIDE.fr.md#read-aloud) | TTS Android suivant les phrases, passage au chapitre suivant, démarrage depuis la sélection, minuterie, réglages de voix/vitesse/hauteur, priorité audio, casque/Bluetooth et notifications |
| [**Recherche**](docs/USER_GUIDE.fr.md#search) | Recherche rapide en texte intégral dans les livres, métadonnées, passages surlignés, notes et noms de chapitres, avec correspondances par préfixe et recherches récentes |
| [**Statistiques**](docs/USER_GUIDE.fr.md#statistics-and-goals) | Temps et séances de lecture, séries de jours, objectifs quotidiens/annuels, livres terminés, activité par date, historique par livre et stockage utilisé (livres, couvertures, notes et citations, dictionnaire, cache ; livres les plus volumineux/petits et par format) |
| [**Widgets et raccourcis**](docs/USER_GUIDE.fr.md#widgets-and-app-shortcuts) | Continuer la lecture avec lecture/pause vocale, graphique adaptatif du temps de lecture sur sept jours et raccourcis vers les principales destinations de la bibliothèque |
| [**Sauvegarde**](docs/USER_GUIDE.fr.md#backup-and-restore) | Sauvegarde/restauration manuelle en ZIP portable et sauvegardes automatiques dans un dossier choisi, quotidiennes, hebdomadaires ou tous les 30 jours, avec réglages de conservation |
| [**Diagnostic**](docs/USER_GUIDE.fr.md#diagnostics-and-support) | Historique local des plantages et de la synchronisation, détails de l’environnement, masquage des données sensibles, copier/partager et accès au support développeur en un toucher |
| [**Langues**](docs/USER_GUIDE.fr.md#settings-and-languages) | Choix de langue dans l’application : anglais, espagnol, portugais, russe, allemand, français, italien, malayalam et tamoul |
| [**Apparence**](docs/USER_GUIDE.fr.md#settings-and-languages) | Material 3, modes système/clair/sombre, noir pur, couleurs Material You facultatives, profils Standard/E‑Ink, réglages de mouvement et navigation adaptative téléphone/tablette |

Vayana permet aussi de [suivre les livres papier et empruntés](docs/USER_GUIDE.fr.md#offline-physical-borrowed-and-other-books) sans fichier numérique, avec progression, dates, évaluations, notes et statistiques.

<a id="install"></a>

## Installation

Vayana prend en charge **Android 8.0 (API 26) et les versions ultérieures**.

Pour découvrir la première utilisation, consultez le [guide de démarrage rapide](docs/USER_GUIDE.fr.md#quick-start).

1. Ouvrez la [dernière version sur GitHub](https://github.com/rjwarrier/Vayana/releases/latest).
2. Téléchargez `Vayana-v0.87.apk`.
3. Autorisez l’installation depuis votre navigateur ou gestionnaire de fichiers si Android le demande, puis ouvrez l’APK.

Android peut signaler que l’application provient de l’extérieur de Google Play. Les fichiers de la version comprennent un fichier `.sha256` pour vérifier le téléchargement avant l’installation.

Pour la v0.87 :

```text
SHA-256 de l’APK
783642D1B39F7941CE0C0A97EACB31CFE3163D50504051012F6E84D5EADEA909

SHA-256 du certificat de publication
53:2C:F4:07:D5:F0:D1:21:58:8A:5C:F1:6E:61:12:C8:F1:BB:3B:7E:D9:CF:F1:39:80:6A:7B:63:C6:43:96:C7
```

<a id="current-scope"></a>

## Périmètre actuel

- **Formats de livres numériques lisibles :** [EPUB](docs/USER_GUIDE.fr.md#reading-epub-books) et [PDF](docs/USER_GUIDE.fr.md#reading-pdf-books). Les PDF numérisés sans couche de texte permettent l’affichage, le zoom et les signets, mais pas la sélection de texte. Les [livres papier](docs/USER_GUIDE.fr.md#offline-physical-borrowed-and-other-books) peuvent être suivis sans fichier.
- **Accès en ligne :** La lecture de base, les notes, le dictionnaire et les statistiques fonctionnent localement. [Project Gutenberg](docs/USER_GUIDE.fr.md#download-free-books-from-project-gutenberg), [OPDS](docs/USER_GUIDE.fr.md#connect-an-opds-catalog), Goodreads, recherche de couvertures, traduction, consultations Wiktionary/Wikipedia et [synchronisation GitHub](docs/USER_GUIDE.fr.md#github-sync) nécessitent Internet lorsqu’ils sont utilisés ; le texte sélectionné est envoyé au fournisseur seulement lorsque vous choisissez cette action. Consultez le [guide de confidentialité et services en ligne](docs/USER_GUIDE.fr.md#privacy-and-online-services).
- **Rafraîchissement E‑Ink :** Le [comportement E‑Ink portable](docs/USER_GUIDE.fr.md#e-ink-display-profile) est implémenté. Les modes de rafraîchissement spécifiques aux fabricants, comme les SDK Onyx/Boox, ne sont pas encore intégrés.
- **Historique dans le cloud :** Les fichiers chiffrés supprimés définitivement peuvent rester dans les anciens commits du dépôt de synchronisation GitHub, car l’historique Git est immuable sauf réécriture. Consultez les [limites actuelles](docs/USER_GUIDE.fr.md#current-limitations).

<a id="build-from-source"></a>

## Compiler à partir du code source

<a id="requirements"></a>

### Prérequis

- JDK 17 ou version ultérieure
- Android SDK 37
- Git

Clonez le dépôt et compilez un APK de débogage avec le wrapper Gradle fourni :

```bash
git clone https://github.com/rjwarrier/Vayana.git
cd Vayana
./gradlew :app:assembleDebug
```

Sous Windows :

```powershell
.\gradlew.bat :app:assembleDebug
```

L’APK de débogage est généré dans `app/build/outputs/apk/debug/`.

Pour compiler aussi le compagnon, exécutez `./gradlew :app:assembleDebug :wear:assembleDebug`
(ou `.\gradlew.bat :app:assembleDebug :wear:assembleDebug` sous Windows).
Installez `wear/build/outputs/apk/debug/wear-debug.apk` sur la montre. Les deux applications doivent
utiliser le même certificat de signature ; consultez [installation et vérification](docs/WEAR_OS.md#install-and-build).

<a id="release-signing"></a>

### Signature des versions publiées

Les identifiants de publication restent hors de Git. Indiquez à la compilation un fichier local de propriétés Java avec `VAYANA_KEYSTORE_PROPERTIES`, comme propriété Gradle ou variable d’environnement :

```properties
storeFile=/absolute/path/to/release-keystore
storePassword=...
keyAlias=...
keyPassword=...
storeType=PKCS12
```

```bash
./gradlew :app:assembleRelease \
  -PVAYANA_KEYSTORE_PROPERTIES=/absolute/path/to/keystore.properties
```

Sans chemin externe, la compilation recherche à la racine du dépôt un fichier `keystore.properties` ignoré par Git.

<a id="project-structure"></a>

## Structure du projet

```text
app/                    Structure de l’application téléphone et navigation
wear/                   Compagnon Wear OS pour la lecture de livres papier
core/wear/              Chronomètre, séances et protocole Data Layer partagés
core/                   Base de données, paramètres, fichiers, sauvegarde, synchronisation, diagnostic, intégration Home Library et système de design
feature/                Bibliothèque, lecteur, découverte, notes, rappels, recherche, statistiques, paramètres et configuration initiale
reader/engine-api/      Contrat du moteur de lecture
reader/engine-web/      Moteur EPUB basé sur Foliate et pont WebView
format/epub/            Métadonnées EPUB et import
format/pdf/             Métadonnées PDF, rendu et texte
format/convert/         Conversion de documents partagée
dictionary/             API de dictionnaire, implémentation StarDict intégrée et consultations en ligne
build-logic/            Conventions de compilation Android et Kotlin partagées
```

L’application utilise Kotlin, Jetpack Compose, Material 3, Room, DataStore, Hilt, WorkManager et une intégration ciblée du moteur de rendu EPUB de Foliate.

<a id="documentation"></a>

## Documentation

- [Site de Vayana](https://ranjithj.in/vayana/)
- [Guide d’utilisation et aide détaillée des fonctionnalités](docs/USER_GUIDE.fr.md)
- [Index de la documentation](docs/README.md)
- [Comportement des fonctionnalités et invariants](docs/FEATURES.md)
- [Configuration et utilisation du compagnon Wear OS](docs/WEAR_OS.md)
- [Changements depuis la v0.87](docs/releases/UNRELEASED.md)
- [Configuration de la synchronisation GitHub](docs/GITHUB_SYNC_SETUP.md)
- [Décisions d’architecture et d’implémentation](docs/DECISIONS.md)
- [Historique des modifications de la base de données](docs/DATABASE_CHANGELOG.md)
- [Conception de la synchronisation GitHub](docs/GITHUB_SYNC_IMPLEMENTATION_PLAN.md)
- [Notes de version v0.87](https://github.com/rjwarrier/Vayana/releases/tag/v0.87)
- [Notes de version v0.85](docs/releases/v0.85.md)

<a id="feedback"></a>

## Retours et suggestions

Utilisez [GitHub Issues](https://github.com/rjwarrier/Vayana/issues) pour les bugs reproductibles et les demandes de fonctionnalités précises. Pour un problème de lecture ou de synchronisation, indiquez la version Android, le modèle de l’appareil, le profil d’affichage et l’[export de diagnostic](docs/USER_GUIDE.fr.md#diagnostics-and-support), si disponible ; ne joignez jamais de jeton GitHub ni de phrase secrète de synchronisation.

<a id="support"></a>

## Soutien

Si Vayana vous est utile, vous pouvez soutenir son développement :

<a href="https://www.buymeacoffee.com/ranjithj"><img alt="Offrez-moi un café" src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=&slug=ranjithj&button_colour=FFDD00&font_colour=000000&font_family=Bree&outline_colour=000000&coffee_colour=ffffff" height="44"></a>
