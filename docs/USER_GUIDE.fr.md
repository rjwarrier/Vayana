<a id="vayana-user-guide"></a>

# Guide d’utilisation de Vayana

[English](USER_GUIDE.md) · [Español](USER_GUIDE.es.md) · [Português (Brasil)](USER_GUIDE.pt.md) · **Français** · [Deutsch](USER_GUIDE.de.md) · [README en français](../README.fr.md)

Ce guide décrit les fonctionnalités disponibles dans Vayana 0.87 et explique les procédures les plus courantes de lecture, de bibliothèque, de sauvegarde, de synchronisation et d’assistance. Vayana est un lecteur Android EPUB et PDF qui privilégie le stockage local. Il suit également les livres papier, empruntés, audio et autres livres sans nécessiter de fichier numérique.

> **Code actuel :** Le [compagnon Wear OS](WEAR_OS.md) et les [corrections récentes de synchronisation](releases/UNRELEASED.md) décrits ci-dessous nécessitent des compilations actuelles. La version publiée v0.87 contient actuellement uniquement l’APK pour téléphone.

> Les captures de ce guide proviennent d’un appareil de développement utilisant des livres du domaine public de Project Gutenberg. La disposition des écrans peut varier selon leur taille, la version Android, le thème, la langue et les paramètres E-Ink.

<a id="contents"></a>

## Sommaire

- [Démarrage rapide](#quick-start)
- [Navigation](#navigation)
- [Ajouter des livres](#adding-books)
- [Gérer la bibliothèque](#managing-the-library)
- [Détails du livre et état de lecture](#book-details-and-reading-status)
- [Lire des livres EPUB](#reading-epub-books)
- [Lire des livres PDF](#reading-pdf-books)
- [Surlignages, notes, signets et citations](#highlights-notes-bookmarks-and-quotes)
- [Dictionnaire, traduction et vocabulaire](#dictionary-translation-and-vocabulary)
- [Lecture à voix haute](#read-aloud)
- [Recherche](#search)
- [Statistiques et objectifs](#statistics-and-goals)
- [Compagnon Wear OS](#wear-os-companion)
- [Widgets et raccourcis de l’application](#widgets-and-app-shortcuts)
- [Sauvegarde et restauration](#backup-and-restore)
- [Synchronisation GitHub](#github-sync)
- [E-Ink et accessibilité](#e-ink-and-accessibility)
- [Paramètres et langues](#settings-and-languages)
- [Diagnostic et assistance](#diagnostics-and-support)
- [Confidentialité et services en ligne](#privacy-and-online-services)
- [Dépannage](#troubleshooting)
- [Limites actuelles](#current-limitations)

<a id="quick-start"></a>

## Démarrage rapide

1. Installez l’APK de la [dernière version GitHub](https://github.com/rjwarrier/Vayana/releases/latest).
2. Terminez la configuration initiale et choisissez le profil d’affichage correspondant à votre appareil : **Standard** ou **E-Ink**.
3. Ouvrez **Livres** et touchez **Ajouter des livres**.
4. Importez un EPUB/PDF, analysez un dossier ou choisissez **Livres gratuits** pour parcourir Project Gutenberg.
5. Touchez une couverture pour ouvrir les **Détails du livre**, puis choisissez **Continuer la lecture**.
6. Touchez le centre d’une page EPUB pour afficher les outils du lecteur et les réglages d’apparence.

Vayana stocke votre bibliothèque localement par défaut. Aucun compte n’est nécessaire. La sauvegarde, la synchronisation GitHub, Goodreads, Project Gutenberg, OPDS, les recherches Wikimedia et la traduction sont facultatifs.

<table>
  <tr>
    <td width="50%" align="center">
      <img src="assets/user-guide/01-library.png" alt="Bibliothèque Vayana avec Aujourd’hui, Continuer la lecture, suggestions et grille de livres du domaine public" width="360"><br>
      <sub><strong>Bibliothèque</strong> : état de lecture, Continuer la lecture, suggestions, filtres et couvertures.</sub>
    </td>
    <td width="50%" align="center">
      <img src="assets/user-guide/02-free-books.png" alt="Explorateur Project Gutenberg avec recherche et filtres de langue, sujet et popularité" width="360"><br>
      <sub><strong>Livres gratuits</strong> : explorez les titres du domaine public par popularité, sujet ou langue.</sub>
    </td>
  </tr>
</table>

<a id="navigation"></a>

## Navigation

La disposition compacte sur téléphone comporte trois destinations principales :

- **Livres** ouvre le tableau de bord de lecture et la bibliothèque.
- **Notes** regroupe les surlignages, soulignements et notes par livre.
- **Statistiques** affiche le temps de lecture, les séries de jours, les objectifs, les livres terminés et l’espace de stockage utilisé.

Le bouton **Ajouter des livres** apparaît à côté de la navigation principale. La barre d’outils de la bibliothèque propose également :

- **Synchroniser maintenant** pour la synchronisation GitHub configurée.
- **Rechercher** dans la bibliothèque et les données de lecture.
- **Autres actions de la bibliothèque** pour les vues Prochaines lectures, étagères, livres sans fichier numérique et Suppressions récentes.
- **Paramètres** pour l’apparence, le comportement du lecteur, la sauvegarde, la synchronisation, les objectifs, l’Aide, À propos et le Diagnostic.

Sur les grands écrans, Vayana utilise des dispositions adaptatives, une navigation latérale et des vues liste/détail lorsque l’espace le permet. Une disposition lecteur/notes à deux colonnes peut être activée pour les écrans larges en paysage.

<a id="adding-books"></a>

## Ajouter des livres

<a id="import-epub-or-pdf-files"></a>

### Importer des fichiers EPUB ou PDF

Depuis **Livres → Ajouter des livres** :

- **Importer des livres** ouvre le sélecteur de fichiers Android pour choisir un ou plusieurs EPUB/PDF.
- **Importer un dossier** analyse le dossier sélectionné et importe les livres compatibles trouvés.
- **Ouvrir avec** d’Android et **Partager avec Vayana** permettent d’importer un fichier compatible depuis une autre application.
- **Remplacer la source**, dans les Détails du livre, remplace un fichier absent ou mis à jour tout en conservant, si possible, l’entrée de bibliothèque et les données de lecture de Vayana.

Vayana détecte les importations probablement dupliquées. Gardez les fichiers source disponibles jusqu’à la fin de l’import. Les PDF protégés par mot de passe sont signalés comme non pris en charge.

<a id="download-free-books-from-project-gutenberg"></a>

### Télécharger des livres gratuits depuis Project Gutenberg

Ouvrez **Livres → Ajouter des livres → Livres gratuits**. Vous pouvez :

- Rechercher par titre ou auteur.
- Passer entre les listes **Populaires**, **Récents** et **Aléatoires**.
- Filtrer par langue et sujets tels qu’aventure, mystère, science-fiction, fantasy, poésie ou littérature jeunesse.
- Ouvrir un titre, comparer les éditions disponibles et choisir des versions avec ou sans images.
- Voir quels livres figurent déjà dans la bibliothèque Vayana.
- Continuer à explorer un catalogue en cache lorsque Project Gutenberg est temporairement indisponible.

Les titres de Project Gutenberg peuvent être libres de droits aux États-Unis sans l’être partout ailleurs. Vérifiez les règles de droit d’auteur de votre pays.

<a id="connect-an-opds-catalog"></a>

### Connecter un catalogue OPDS

Choisissez **Catalogues en ligne** sur l’écran Livres gratuits. Ajoutez l’adresse OPDS fournie par un service tel que :

- Le serveur de contenu Calibre
- Calibre-Web
- Standard Ebooks
- Un autre catalogue OPDS compatible

Ouvrez un catalogue pour parcourir ses flux de navigation, rechercher si le serveur le permet et télécharger des livres EPUB ou PDF compatibles. L’authentification et la disponibilité dépendent du serveur du catalogue.

<p align="center">
  <img src="assets/user-guide/03-online-catalogs.png" alt="Écran Catalogues en ligne vide expliquant comment ajouter un catalogue OPDS" width="360"><br>
  <sub>Ajoutez Calibre, Calibre-Web, Standard Ebooks ou un autre catalogue OPDS.</sub>
</p>

<a id="managing-the-library"></a>

## Gérer la bibliothèque

<a id="dashboard-and-filters"></a>

### Tableau de bord et filtres

La bibliothèque associe le catalogue à un tableau de bord de lecture :

- **Aujourd’hui** affiche la progression vers l’objectif quotidien et les révisions à effectuer.
- **Continuer la lecture** revient au livre disponible localement le plus récemment utilisé.
- **Suggestions de lecture** utilise le contexte actuel de la bibliothèque pour proposer des livres liés.
- Les filtres d’état comprennent **Tous**, **En cours**, **Terminés**, **Non commencés**, **En pause** et **Abandonnés**.
- La recherche porte sur le titre, l’auteur et la description.
- Les vues grille/liste, le tri, les étagères, les séries, Prochaines lectures et les autres filtres facilitent l’organisation des grandes bibliothèques.

<a id="read-next-shelves-and-series"></a>

### Prochaines lectures, étagères et séries

- Ajoutez des livres à **Prochaines lectures** et réorganisez la file.
- Créez des étagères pour vos collections personnelles et vos filtres.
- Utilisez les métadonnées de séries pour regrouper les livres liés et conserver leur ordre.
- Un livre peut conserver étiquettes, évaluation, dates, progression, notes et statistiques de lecture avec ses métadonnées de fichier.

<a id="offline-physical-borrowed-and-other-books"></a>

### Livres sans fichier numérique, papier, empruntés et autres

Les entrées de livres sans fichier numérique ne nécessitent pas d’EPUB ou de PDF. Utilisez-les pour les livres papier, les livres audio ou les formats lus dans une autre application. Vous pouvez enregistrer :

- Titre, auteur, couverture, série, étiquettes et nombre de pages
- État et dates de lecture
- Pages lues ou progression en pourcentage
- Évaluations, notes et séances de lecture
- Statut **Possédé** ou **Emprunté**
- Date de retour des livres empruntés

Lorsque les rappels d’emprunt sont activés, ils peuvent vous prévenir trois jours avant, un jour avant et le jour du retour.

<a id="home-library-mirror"></a>

### Miroir Home Library

Si l’application Home Library du développeur est installée sur le même appareil et si le partage est activé, Vayana peut reproduire son catalogue dans les Livres sans fichier numérique :

- Activez **Synchroniser avec Home Library** dans les Paramètres.
- Les champs du catalogue reproduit sont en lecture seule ; Home Library reste responsable de ces données.
- Vayana conserve ses propres progression, dates, état Prochaines lectures, notes et statistiques.
- L’emplacement sur l’étagère et les métadonnées partagées apparaissent dans la recherche et les Détails du livre.
- **Voir dans Home Library** revient à la fiche source.

<a id="book-details-and-reading-status"></a>

## Détails du livre et état de lecture

Les Détails du livre réunissent métadonnées et activité de lecture. Selon le livre, ils peuvent afficher :

- Couverture, titre, auteur, série, format et progression
- Description, informations de l’éditeur, étiquettes, sujets et métadonnées personnalisées
- Évaluation personnelle et état de lecture
- Dates de début, de fin et de dernière lecture
- Temps passé à lire et progression actuelle
- Notes, surlignages, citations et nombres de chapitres
- Actions sur les fichiers, remplacement de source, partage et modification de couverture
- Métadonnées, évaluation, genres, séries et citations de la communauté Goodreads, facultativement

Les modifications de métadonnées et de couverture affectent l’entrée locale de Vayana, sauf si le livre est un miroir Home Library en lecture seule.

<p align="center">
  <img src="assets/user-guide/04-book-details.png" alt="Détails du livre Le Chien des Baskerville" width="360"><br>
  <sub>Les Détails du livre réunissent métadonnées, état, progression, dates, notes et actions.</sub>
</p>

<a id="reading-epub-books"></a>

## Lire des livres EPUB

Le lecteur EPUB de Vayana mémorise la position de chaque livre et prend en charge le texte redistribuable, les EPUB à mise en page fixe et les choix de présentation par livre.

<a id="reader-navigation"></a>

### Navigation du lecteur

- Touchez ou faites glisser pour changer de page selon les zones tactiles et le comportement configurés.
- Utilisez les touches physiques de volume/pagination lorsqu’elles sont activées.
- Ouvrez **Sommaire** pour accéder à un chapitre.
- Ajoutez des signets et revenez-y.
- Recherchez dans le livre actuel.
- Consultez la progression par page/position et en pourcentage.
- Réglez le plein écran, les informations d’en-tête/pied de page et le maintien de l’écran allumé selon vos préférences.
- En paysage, les écrans larges peuvent afficher deux colonnes.

Après une absence, Vayana peut afficher un bref récapitulatif **Bon retour** avec le dernier chapitre et le contexte récent de lecture.

<a id="typography-and-page-appearance"></a>

### Typographie et apparence des pages

Ouvrez les **Paramètres** dans le lecteur pour régler :

- Thème de page : Système, Clair, Papier, Sépia, Menthe ou autres thèmes disponibles
- Famille de police, police personnalisée importée, taille et texte plus épais
- Hauteur de ligne, alignement, césure et marges latérales
- Styles de l’éditeur et remplacement éventuel de la typographie du livre par Vayana
- Contenu de l’en-tête/pied de page et style de progression
- Lecture bionique facultative
- Préréglages de lecture enregistrés et styles personnalisés par livre

Les thèmes peuvent s’appliquer globalement, tandis que l’option de style personnalisé préserve les choix typographiques propres au livre.

<table>
  <tr>
    <td width="50%" align="center">
      <img src="assets/user-guide/06-reader.png" alt="Lecteur EPUB montrant un chapitre de Sherlock Holmes du domaine public avec un thème sombre" width="360"><br>
      <sub><strong>Lecteur</strong> : texte sans distraction, avec progression et récapitulatif au retour.</sub>
    </td>
    <td width="50%" align="center">
      <img src="assets/user-guide/07-reader-controls.png" alt="Commandes du lecteur et réglages typographiques" width="360"><br>
      <sub><strong>Commandes du lecteur</strong> : sommaire, notes, progression, style, lecture à voix haute et recherche.</sub>
    </td>
  </tr>
</table>

<a id="reading-pdf-books"></a>

## Lire des livres PDF

Les fichiers PDF peuvent être importés par le sélecteur de fichiers, l’analyse de dossiers, Ouvrir avec, Partager ou Remplacer la source.

Le lecteur PDF prend en charge :

- Une page par écran avec changement de page tactile et navigation par touches physiques
- La navigation par plan/sommaire PDF lorsque le document le fournit
- La navigation par numéro de page et la position mémorisée
- L’ajustement à la page, l’ajustement à la largeur, le zoom et le déplacement
- Les commandes de luminosité/lumière chaude sur les bords lorsqu’elles sont configurées
- Les signets dans tous les PDF
- La sélection de texte, copie, dictionnaire, surlignages, soulignements, notes et cartes de citations dans les PDF possédant une couche de texte
- La génération de couverture à partir de la première page et l’extraction du titre/auteur depuis les métadonnées PDF lorsqu’elles existent

Les PDF constitués d’images numérisées sans couche de texte peuvent être affichés, agrandis, déplacés et marqués de signets, mais leur texte ne peut pas être sélectionné ou recherché. Les polices et espacements PDF sont inscrits dans la page : les réglages typographiques EPUB ne s’appliquent pas.

<a id="highlights-notes-bookmarks-and-quotes"></a>

## Surlignages, notes, signets et citations

<a id="while-reading"></a>

### Pendant la lecture

Sélectionnez du texte pour accéder à des actions telles que :

- Surligner ou souligner
- Ajouter/modifier une note
- Copier ou partager le texte sélectionné
- Créer une carte visuelle de citation
- Définir, rechercher ou traduire la sélection
- Ajouter des étiquettes d’annotation

Les surlignages EPUB superposés peuvent être fusionnés. Les notes de bas de page peuvent s’ouvrir en fenêtres contextuelles lorsque le livre le permet.

<a id="notes-library"></a>

### Bibliothèque de notes

La destination globale **Notes** regroupe les annotations par livre et affiche leurs nombres et dates de mise à jour. Elle permet :

- La recherche dans les notes, livres et passages surlignés
- Les filtres et le retour direct au passage d’origine
- La modification et le partage des annotations
- L’export Markdown
- Les carnets Markdown automatiques lorsqu’ils sont configurés
- L’import Kindle de `My Clippings.txt`
- L’import de citations de la communauté Goodreads, distinctes des annotations personnelles
- La révision espacée des surlignages avec **Revoir bientôt**, **Compris** et **Bien connu**

<p align="center">
  <img src="assets/user-guide/08-notes.png" alt="Bibliothèque de notes regroupées par livres du domaine public" width="360"><br>
  <sub>Les notes sont regroupées par livre avec les nombres d’annotations et de chapitres.</sub>
</p>

<a id="dictionary-translation-and-vocabulary"></a>

## Dictionnaire, traduction et vocabulaire

- Touchez deux fois un mot dans une vue de texte compatible pour ouvrir le dictionnaire hors ligne intégré.
- Vayana utilise la langue du livre pour les consultations lorsque les métadonnées de langue existent.
- Recherchez les mots ou expressions sélectionnés dans Wiktionary ou Wikipedia lorsque vous disposez d’Internet.
- Envoyez une sélection vers une application de traduction installée.
- Enregistrez les mots consultés dans la liste de vocabulaire.
- Découvrez les mots inhabituels du chapitre actuel.
- Marquez les termes familiers comme connus.
- Révisez le vocabulaire enregistré à intervalles espacés.
- Exportez le vocabulaire en CSV compatible avec Anki ou en Markdown lisible.

Les consultations en ligne envoient uniquement la requête sélectionnée au fournisseur choisi lorsque vous le demandez. La traduction nécessite une application compatible.

<a id="read-aloud"></a>

## Lecture à voix haute

La synthèse vocale Android peut lire un EPUB phrase par phrase et poursuivre entre les chapitres.

- Commencez à la position actuelle ou à un passage sélectionné.
- Suivez le marquage du mot/de la phrase prononcé dans le texte.
- Choisissez un moteur TTS installé, une langue, une voix, une vitesse et une hauteur de voix.
- Utilisez une minuterie d’arrêt.
- Mettez en pause en touchant le lecteur.
- Contrôlez la lecture depuis les notifications, l’écran verrouillé, le casque ou les commandes Bluetooth.
- La gestion de la priorité audio met en pause ou atténue la lecture lorsqu’une autre application a besoin de son.
- Le widget Continuer la lecture peut lancer ou mettre en pause la lecture à voix haute du livre affiché.

Les voix et langues disponibles dépendent des moteurs TTS installés sur l’appareil. La lecture à voix haute est principalement conçue pour le texte redistribuable, pas pour les PDF numérisés.

<a id="search"></a>

## Recherche

La recherche globale peut trouver :

- Titres, auteurs, descriptions, séries, étiquettes et autres métadonnées
- Noms de chapitres EPUB et texte indexé des livres
- Surlignages et notes personnels
- Requêtes récentes et correspondances par préfixe

Le contenu EPUB disponible localement est indexé pour une recherche rapide. Le texte des pages PDF et le contenu des livres papier ne sont pas indexés, mais leurs annotations personnelles peuvent apparaître dans la recherche d’annotations.

<a id="statistics-and-goals"></a>

## Statistiques et objectifs

Vayana enregistre les séances de lecture et présente :

- Temps de lecture quotidien et total
- Séries de jours de lecture et activité en calendrier
- Livres terminés par mois et par année
- Objectifs quotidiens en minutes et annuels en livres
- Historique de lecture par livre
- Stockage utilisé par les livres, couvertures, notes/citations, dictionnaires et cache
- Totaux par format et livres les plus volumineux/les plus petits

Les statistiques reposent sur l’activité enregistrée par Vayana. Les séances synchronisées depuis un autre appareil peuvent contribuer après synchronisation.

<p align="center">
  <img src="assets/user-guide/05-statistics.png" alt="Écran Statistiques avec activité de lecture, objectifs et stockage" width="360"><br>
  <sub>Activité de lecture, objectifs, totaux de la bibliothèque et utilisation du stockage.</sub>
</p>

<a id="wear-os-companion"></a>

## Compagnon Wear OS

Le compagnon suit la lecture de livres papier sur Wear OS 3+, avec les services Google Play et un téléphone Android associé. Configurez-le avec des compilations actuelles compatibles pour téléphone et montre en suivant le [guide Wear OS](WEAR_OS.md#install-and-build).

1. Ajoutez un livre papier sur le téléphone et marquez-le comme lecture en cours.
2. Ouvrez Vayana sur la montre et choisissez **Synchroniser avec le téléphone** (Sync with phone) pour mettre vos livres en cache.
3. Sélectionnez un livre, vérifiez la page de départ et touchez **Démarrer le chronomètre** (Start timer). Les chronomètres gérés par la montre, les pauses et les modifications de page fonctionnent hors ligne.
4. Touchez **Arrêter** (Stop), saisissez la page de fin, puis **Enregistrer** (Save). Annuler laisse en pause un chronomètre démarré sur la montre.
5. Reconnectez-vous pour envoyer les séances enregistrées à l’historique et aux statistiques du téléphone. Les séances en attente restent sur la montre jusqu’à confirmation.

Un chronomètre lancé sur le téléphone peut aussi apparaître sur la montre, où vous pouvez le mettre en pause, le reprendre, l’arrêter et modifier sa page. Les actions à distance attendent la confirmation de l’appareil responsable ; l’affichage d’un chronomètre hors ligne ne confirme pas l’application d’une commande. Le téléphone permet également de contrôler un chronomètre géré par la montre.

Utilisez **Objectifs de lecture** (Reading goals) pour un objectif quotidien ou un rappel de séance, et **Vibration** pour le retour haptique. Le compagnon comprend un affichage ambiant, une tuile de lecture et une complication de cadran. Pour les conflits de page, la révision des chevauchements, la récupération après redémarrage et les indicateurs de connexion, consultez le [guide complet du compagnon](WEAR_OS.md).

La montre n’ouvre pas les fichiers de livres numériques. Sa connexion compagnon utilise le Data Layer de Google, indépendamment de la synchronisation GitHub facultative.

<a id="widgets-and-app-shortcuts"></a>

## Widgets et raccourcis de l’application

Vayana propose deux widgets configurables pour l’écran d’accueil :

- **Continuer la lecture** affiche le livre actuel, sa couverture, la progression et le retour à la lecture en un toucher. Lorsque la lecture à voix haute est activée, il peut afficher lecture/pause.
- **Temps de lecture** affiche les minutes du jour et un graphique adaptatif sur sept jours avec une moyenne.

L’écran de configuration du widget prévisualise les modifications et applique des choix partagés de rayon des coins et de style de progression aux widgets Vayana. La disposition s’adapte à la taille choisie dans le lanceur.

Appuyez longuement sur l’icône Vayana du lanceur pour accéder aux raccourcis :

- Livres gratuits
- Recherche
- Statistiques

<a id="backup-and-restore"></a>

## Sauvegarde et restauration

<a id="manual-backup"></a>

### Sauvegarde manuelle

Ouvrez **Paramètres → Sauvegarde et restauration** pour créer un ZIP portable contenant la base de données de bibliothèque, les paramètres, les fichiers de livres gérés par Vayana, les couvertures et les données de lecture associées.

<a id="automatic-backup"></a>

### Sauvegarde automatique

Choisissez un dossier, une fréquence et un nombre de copies à conserver. Les fréquences disponibles sont quotidienne, tous les sept jours et tous les 30 jours. Android peut différer les tâches en arrière-plan selon les restrictions de batterie et de l’appareil.

<a id="restore-safely"></a>

### Restaurer en toute sécurité

Vayana affiche un résumé de la sauvegarde avant restauration : date de création, version de l’application, nombre de livres, nombre d’annotations et taille. La restauration remplace la bibliothèque et les paramètres actuels de l’appareil, puis redémarre l’application. Créez d’abord une sauvegarde récente si vous risquez d’avoir encore besoin de la bibliothèque actuelle.

La sauvegarde est distincte de la synchronisation GitHub : une sauvegarde est une archive portable à un instant donné ; la synchronisation fusionne les données prises en charge entre les appareils configurés.

<a id="github-sync"></a>

## Synchronisation GitHub

La synchronisation GitHub utilise un dépôt que vous contrôlez. Elle peut synchroniser :

- Livres, couvertures et métadonnées de la bibliothèque
- Position de lecture, séances et dates
- Surlignages, notes, étagères et Prochaines lectures
- Vocabulaire et paramètres portables
- Suppressions et restaurations

Les fichiers des livres et couvertures sont chiffrés avec AES-GCM à l’aide de la phrase secrète de synchronisation avant l’envoi. Le jeton GitHub est stocké sur l’appareil. La synchronisation légère de progression peut se dérouler pendant la lecture, tandis que la synchronisation complète rapproche l’état de la bibliothèque et ses fichiers.

Règles importantes de configuration :

- Utilisez un dépôt privé dédié à la synchronisation Vayana.
- Accordez au jeton uniquement l’accès au dépôt dont il a besoin.
- Utilisez le même dépôt, la même branche et la même phrase secrète sur tous les appareils.
- Donnez à chaque appareil un nom reconnaissable pour les messages de conflit et d’historique.
- Conservez la phrase secrète en lieu sûr ; les fichiers chiffrés ne peuvent pas être récupérés sans elle.
- Utilisez **Tester la connexion GitHub** avant la première synchronisation complète.

Consultez [Configurer la synchronisation GitHub](GITHUB_SYNC_SETUP.md) pour la procédure complète et la liste de vérifications de dépannage.

<a id="e-ink-and-accessibility"></a>

## E-Ink et accessibilité

<a id="e-ink-display-profile"></a>

### Profil d’affichage E-Ink

E-Ink est un profil dédié plutôt qu’un simple thème en niveaux de gris. Il peut :

- Supprimer ou réduire les mouvements et l’animation de changement de page
- Afficher une progression statique
- Proposer les palettes **Monochrome** et **Couleur** à fort contraste
- Lier les actualisations du lecteur aux changements de page lorsque c’est possible
- Utiliser les touches physiques de pagination
- Ajouter des commandes pour avancer d’un écran à la fois dans les écrans longs
- Demander des rafraîchissements de nettoyage aux intervalles et transitions configurés
- Proposer un texte plus épais, l’alignement et la césure

Les API de rafraîchissement spécifiques aux fabricants ne sont pas intégrées actuellement : le comportement dépend donc de l’appareil Android et de son propre mode de rafraîchissement.

<a id="accessibility-and-adaptive-layout"></a>

### Accessibilité et disposition adaptative

- Les composants Material 3 fournissent des descriptions de contenu et du texte redimensionnable.
- Les mouvements peuvent être Complets, Réduits ou Désactivés.
- Des apparences standard, sombre adoucie et noir pur sont disponibles.
- Les dispositions téléphone et tablette adaptent la navigation et la largeur du contenu.
- Le texte, les marges, le contraste et l’interligne du lecteur se règlent indépendamment.

<a id="settings-and-languages"></a>

## Paramètres et langues

Les paramètres sont regroupés et peuvent être recherchés :

- **Apparence :** thème, couleurs, E-Ink, mouvements, navigation, dates et langue de l’application
- **Bibliothèque :** écran initial, couvertures, comportement en fin de lecture, livres sans fichier numérique, Home Library, rappels et Suppressions récentes
- **Texte du lecteur :** police, taille, espacement, styles du livre et polices personnalisées
- **Page du lecteur :** couleurs de page, marges, en-tête, pied de page et présentation de la progression
- **Commandes du lecteur :** toucher, touches, surlignage, maintien de l’écran allumé, lecture à voix haute et commandes associées
- **Objectifs de lecture :** minutes quotidiennes, livres annuels et début de semaine
- **Synchronisation :** dépôt GitHub, identifiants, phrase secrète, nom de l’appareil, état, tests et transfert
- **Sauvegarde et restauration :** sauvegardes manuelles et programmées
- **Aide et à propos :** aide intégrée, Diagnostic, version, code source, lien de publication, partage et liens du développeur

La langue de l’interface Vayana se choisit indépendamment de celle des livres. La version 0.87 comprend anglais, espagnol, portugais, russe, allemand, français, italien, malayalam et tamoul. Recherchez **Langue** dans les Paramètres pour changer la langue de l’application ; les livres conservent leurs propres métadonnées de langue.

<p align="center">
  <img src="assets/user-guide/11-settings.png" alt="Catégories de Paramètres avec recherche" width="360"><br>
  <sub>Les groupes de paramètres recherchables séparent les options globales de celles du lecteur.</sub>
</p>

<a id="diagnostics-and-support"></a>

## Diagnostic et assistance

Ouvrez **Paramètres → Aide et à propos → Diagnostic** lorsque l’application plante ou que la synchronisation se comporte de façon inattendue.

Le Diagnostic enregistre localement les plantages non gérés et les problèmes récents de synchronisation. L’écran affiche des nombres, les événements du plus récent au plus ancien et des détails techniques dépliables. Il propose également :

- **Partager avec le développeur** pour ouvrir le panneau de partage Android avec un rapport en texte brut
- **Copier le rapport** pour le placer dans le presse-papiers
- **Effacer tous les journaux** pour supprimer les événements de diagnostic enregistrés sur l’appareil

Le rapport généré comprend la version de l’application, la version Android, le modèle de l’appareil, les heures des événements, les sources, les messages et les traces techniques. Il ne comprend pas le contenu privé des livres, les notes ou les paramètres. Les valeurs d’autorisation courantes, paramètres secrets des URL, motifs de jetons GitHub, chemins utilisateurs, chemins de fichiers externes, URI de contenu et adresses e-mail sont masqués. Relisez toujours le rapport avant de le partager.

Lorsque vous signalez un problème, indiquez :

- Ce que vous faisiez
- Ce que vous attendiez
- Ce qui s’est produit à la place
- Si cela se produit à chaque fois
- Le format du livre et si le problème concerne un seul livre ou tous les livres
- Le rapport de diagnostic, si pertinent

Ne publiez jamais de jeton GitHub ou de phrase secrète de synchronisation dans un ticket.

<table>
  <tr>
    <td width="50%" align="center">
      <img src="assets/user-guide/10-diagnostics.png" alt="Écran Diagnostic avec actions de partage et de copie" width="360"><br>
      <sub><strong>Diagnostic</strong> : examinez et partagez les informations de plantage ou de synchronisation expurgées des données sensibles.</sub>
    </td>
    <td width="50%" align="center">
      <img src="assets/user-guide/09-about.png" alt="Écran À propos de Vayana avec version, source, publications, soutien et autres applications" width="360"><br>
      <sub><strong>À propos</strong> : version, code source, publications, soutien, partage et liens du développeur.</sub>
    </td>
  </tr>
</table>

Signalez les problèmes reproductibles via [GitHub Issues](https://github.com/rjwarrier/Vayana/issues).

<a id="privacy-and-online-services"></a>

## Confidentialité et services en ligne

La lecture de base, la recherche locale, les notes, le dictionnaire hors ligne, les statistiques et les sauvegardes locales fonctionnent sans compte Vayana.

L’accès réseau intervient lorsque vous choisissez ou configurez une fonctionnalité en ligne :

| Fonctionnalité | Données concernées |
| --- | --- |
| Project Gutenberg | Requêtes de recherche/filtrage et téléchargements de livres |
| OPDS | Adresse du catalogue, requêtes de navigation/recherche, authentification facultative du serveur et téléchargements |
| Goodreads | Recherche de métadonnées/couvertures et recours au navigateur |
| Wiktionary/Wikipedia | Mot ou expression sélectionné lorsque vous demandez une consultation |
| Traduction | Texte sélectionné transmis à l’application/au fournisseur de traduction choisi |
| Synchronisation GitHub | Métadonnées de synchronisation et fichiers de livres/couvertures chiffrés avec AES-GCM ; les identifiants du dépôt et le jeton restent dans les paramètres de l’application sur l’appareil |
| Recherche de couvertures | Métadonnées de recherche du livre nécessaires à la source choisie |

Les journaux de diagnostic restent sur l’appareil jusqu’à ce que vous les copiiez, partagiez ou effaciez explicitement. L’application destinataire choisie dans Android détermine ce qui se passe après le partage.

<a id="troubleshooting"></a>

## Dépannage

<a id="a-book-will-not-import"></a>

### Un livre ne s’importe pas

- Vérifiez qu’il s’agit d’un EPUB ou PDF valide et qu’un autre lecteur peut l’ouvrir.
- Les PDF protégés par mot de passe ne sont pas pris en charge.
- Si Android indique un type de fichier générique, conservez l’extension `.epub` ou `.pdf`.
- Essayez le sélecteur de fichiers Vayana plutôt que Partager/Ouvrir avec.
- Vérifiez l’espace disponible et autorisez l’accès au fichier/dossier sélectionné.

<a id="a-book-is-missing-after-moving-files"></a>

### Un livre manque après le déplacement de fichiers

- Ouvrez les Détails du livre et utilisez **Remplacer la source** pour reconnecter l’entrée.
- S’il a été supprimé définitivement de Vayana, réimportez le fichier.
- Vérifiez **Suppressions récentes** s’il a été retiré récemment et restaurez-le depuis cette vue.

<a id="project-gutenberg-or-opds-does-not-load"></a>

### Project Gutenberg ou OPDS ne se charge pas

- Vérifiez la connexion Internet et réessayez.
- Project Gutenberg peut répondre lentement ; Vayana peut afficher le catalogue enregistré hors ligne.
- Pour OPDS, vérifiez l’URL exacte du catalogue et l’accessibilité du serveur.
- Vérifiez les identifiants du serveur en dehors de Vayana si l’authentification échoue.

<a id="dictionary-or-translation-is-unavailable"></a>

### Le dictionnaire ou la traduction n’est pas disponible

- Vérifiez que la langue du livre est correcte dans ses métadonnées.
- Les résultats hors ligne dépendent des données du dictionnaire installé/intégré.
- Le recours à Wikimedia nécessite Internet.
- La traduction nécessite une application compatible.

<a id="read-aloud-has-no-voice"></a>

### La lecture à voix haute n’a pas de voix

- Installez ou activez un moteur TTS Android et la voix de la langue requise.
- Vérifiez le volume multimédia et la sortie Bluetooth.
- Vérifiez le moteur, la voix, la langue, la vitesse et la hauteur dans les paramètres lecteur/audio.
- Sur les appareils E-Ink, vérifiez que **Fonctionnalités audio** est activé.

<a id="github-sync-fails"></a>

### La synchronisation GitHub échoue

- Ouvrez **Paramètres → Synchronisation** et lancez **Tester la connexion GitHub**.
- Vérifiez propriétaire, dépôt, branche, jeton et phrase secrète.
- Assurez-vous que le jeton a accès au dépôt configuré.
- Utilisez la même phrase secrète sur tous les appareils.
- Ouvrez le Diagnostic et partagez l’événement de synchronisation expurgé si l’erreur reste incomprise.

<a id="statistics-look-incomplete"></a>

### Les statistiques semblent incomplètes

- Seules les séances enregistrées par Vayana sont comptées.
- Vérifiez que le livre est ouvert via Vayana et que l’horloge de l’appareil est correcte.
- Lancez la synchronisation si l’activité a été enregistrée sur un autre appareil configuré.

<a id="the-app-crashed"></a>

### L’application a planté

1. Rouvrez Vayana.
2. Allez dans **Paramètres → Aide et à propos → Diagnostic**.
3. Dépliez le plantage le plus récent et vérifiez que l’heure correspond.
4. Utilisez **Partager avec le développeur** ou **Copier le rapport**.
5. Décrivez l’action effectuée immédiatement avant le plantage.

<a id="current-limitations"></a>

## Limites actuelles

- Les formats numériques de lecture pris en charge sont EPUB et PDF.
- Les PDF numérisés sans couche de texte ne permettent pas la sélection, la consultation, la recherche ou les annotations textuelles.
- La typographie PDF est fixée par le document ; les réglages de police et d’espacement EPUB ne s’appliquent pas.
- La prise en charge PDF ne comprend pas encore toutes les fonctionnalités propres à EPUB, comme la lecture bionique ou les citations de la communauté.
- Les PDF protégés par mot de passe ne sont pas pris en charge.
- Le contenu des livres papier et le texte des pages PDF ne font pas partie de l’indexation intégrale des livres.
- Les SDK de rafraîchissement E-Ink propres aux fabricants ne sont pas intégrés.
- L’historique Git peut conserver des fichiers chiffrés dans les anciens commits de synchronisation après une suppression définitive dans le cloud, sauf réécriture de l’historique du dépôt.
- Les fonctionnalités en ligne de catalogue, enrichissement, consultation, traduction et synchronisation dépendent de la disponibilité des services tiers.

<a id="related-documentation"></a>

## Documentation associée

- [Comportement des fonctionnalités et détails d’implémentation](FEATURES.md)
- [Configuration de la synchronisation GitHub](GITHUB_SYNC_SETUP.md)
- [Décisions d’architecture et d’implémentation](DECISIONS.md)
- [Historique des modifications de la base de données](DATABASE_CHANGELOG.md)
- [Version Vayana 0.87](https://github.com/rjwarrier/Vayana/releases/tag/v0.87)
