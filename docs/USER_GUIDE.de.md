<a id="vayana-user-guide"></a>

# Vayana-Benutzerhandbuch

[English](USER_GUIDE.md) · [Español](USER_GUIDE.es.md) · [Português (Brasil)](USER_GUIDE.pt.md) · [Français](USER_GUIDE.fr.md) · **Deutsch** · [README auf Deutsch](../README.de.md)

Dieses Handbuch beschreibt die Funktionen von Vayana 0.90 und erklärt die häufigsten Abläufe für Lesen, Bibliothek, Sicherung, Synchronisierung und Support. Vayana ist ein Android-Reader für EPUB- und PDF-Bücher mit bevorzugter lokaler Speicherung. Die App erfasst außerdem gedruckte und ausgeliehene Bücher, Hörbücher und weitere Bücher ohne digitale Datei.

> **Version 0.90:** Lade beide signierten APKs von der [Versionsseite](https://github.com/rjwarrier/Vayana/releases/tag/v0.90) für die [Wear-OS-Begleit-App](WEAR_OS.md) herunter. Die [Versionshinweise](releases/v0.90.md) beschreiben Änderungen seit v0.87.

> Die Bildschirmfotos in diesem Handbuch wurden auf einem Entwicklungsgerät mit gemeinfreien Büchern von Project Gutenberg aufgenommen. Das Layout kann je nach Bildschirmgröße, Android-Version, Thema, Sprache und E-Ink-Einstellungen abweichen.

<a id="contents"></a>

## Inhalt

- [Schnellstart](#quick-start)
- [Navigation](#navigation)
- [Bücher hinzufügen](#adding-books)
- [Bibliothek verwalten](#managing-the-library)
- [Buchdetails und Lesestatus](#book-details-and-reading-status)
- [EPUB-Bücher lesen](#reading-epub-books)
- [PDF-Bücher lesen](#reading-pdf-books)
- [Markierungen, Notizen, Lesezeichen und Zitate](#highlights-notes-bookmarks-and-quotes)
- [Wörterbuch, Übersetzung und Vokabeln](#dictionary-translation-and-vocabulary)
- [Vorlesen](#read-aloud)
- [Suche](#search)
- [Statistiken und Ziele](#statistics-and-goals)
- [Wear-OS-Begleit-App](#wear-os-companion)
- [Widgets und App-Verknüpfungen](#widgets-and-app-shortcuts)
- [Sicherung und Wiederherstellung](#backup-and-restore)
- [GitHub-Synchronisierung](#github-sync)
- [E-Ink und Barrierefreiheit](#e-ink-and-accessibility)
- [Einstellungen und Sprachen](#settings-and-languages)
- [Diagnose und Support](#diagnostics-and-support)
- [Datenschutz und Online-Dienste](#privacy-and-online-services)
- [Fehlerbehebung](#troubleshooting)
- [Aktuelle Einschränkungen](#current-limitations)

<a id="quick-start"></a>

## Schnellstart

1. Installiere die APK aus der [neuesten GitHub-Veröffentlichung](https://github.com/rjwarrier/Vayana/releases/latest).
2. Schließe die Ersteinrichtung ab und wähle das passende Anzeigeprofil für dein Gerät: **Standard** oder **E-Ink**.
3. Öffne **Bücher** und tippe auf **Bücher hinzufügen**.
4. Importiere EPUB/PDF, durchsuche einen Ordner oder wähle **Kostenlose Bücher**, um Project Gutenberg zu durchsuchen.
5. Tippe auf ein Cover, um die **Buchdetails** zu öffnen, und wähle **Weiterlesen**.
6. Tippe in die Mitte einer EPUB-Seite, um die Reader-Werkzeuge und Darstellungseinstellungen anzuzeigen.

Vayana speichert die Bibliothek standardmäßig lokal. Ein Konto ist nicht erforderlich. Sicherung, GitHub-Synchronisierung, Goodreads, Project Gutenberg, OPDS, Wikimedia-Nachschlagen und Übersetzung sind optional.

<table>
  <tr>
    <td width="50%" align="center">
      <img src="assets/user-guide/01-library.png" alt="Vayana-Bibliothek mit Heute, Weiterlesen, Vorschlägen und einem Raster gemeinfreier Bücher" width="360"><br>
      <sub><strong>Bibliothek</strong>: Lesestatus, Weiterlesen, Vorschläge, Filter und Buchcover.</sub>
    </td>
    <td width="50%" align="center">
      <img src="assets/user-guide/02-free-books.png" alt="Project-Gutenberg-Browser mit Suche und Filtern für Sprache, Thema und Beliebtheit" width="360"><br>
      <sub><strong>Kostenlose Bücher</strong>: gemeinfreie Titel nach Beliebtheit, Thema oder Sprache durchsuchen.</sub>
    </td>
  </tr>
</table>

<a id="navigation"></a>

## Navigation

Das kompakte Smartphone-Layout bietet drei Hauptbereiche:

- **Bücher** öffnet die Leseübersicht und Bibliothek.
- **Notizen** gruppiert Markierungen, Unterstreichungen und Notizen nach Buch.
- **Statistiken** zeigt Lesezeit, Leseserien, Ziele, abgeschlossene Bücher und Speicherverbrauch.

Die Schaltfläche **Bücher hinzufügen** erscheint neben der Hauptnavigation. Die Bibliotheksleiste bietet außerdem:

- **Jetzt synchronisieren** für die konfigurierte GitHub-Synchronisierung.
- **Suchen** in Bibliothek und Lesedaten.
- **Weitere Bibliotheksaktionen** für Ansichten wie Als Nächstes lesen, Regale, Bücher ohne digitale Datei und Kürzlich gelöscht.
- **Einstellungen** für Darstellung, Reader-Verhalten, Sicherung, Synchronisierung, Ziele, Hilfe, Über und Diagnose.

Auf größeren Bildschirmen verwendet Vayana adaptive Layouts, seitliche Navigation und Listen-/Detailansichten, soweit Platz vorhanden ist. Für breite Bildschirme im Querformat lässt sich eine zweispaltige Reader-/Notizansicht aktivieren.

<a id="adding-books"></a>

## Bücher hinzufügen

<a id="import-epub-or-pdf-files"></a>

### EPUB- oder PDF-Dateien importieren

Unter **Bücher → Bücher hinzufügen**:

- **Bücher importieren** öffnet die Android-Dateiauswahl für eine oder mehrere EPUB-/PDF-Dateien.
- **Ordner importieren** durchsucht einen ausgewählten Ordner und importiert gefundene unterstützte Bücher.
- Android **Öffnen mit** und **Mit Vayana teilen** können eine unterstützte Datei aus einer anderen App importieren.
- **Quelle ersetzen** in den Buchdetails ersetzt eine fehlende oder aktualisierte Buchdatei und bewahrt den Bibliothekseintrag und die Lesedaten soweit möglich.

Vayana erkennt wahrscheinliche doppelte Importe. Halte die Originaldateien verfügbar, bis der Import abgeschlossen ist. Passwortgeschützte PDFs werden als nicht unterstützt gemeldet.

<a id="download-free-books-from-project-gutenberg"></a>

### Kostenlose Bücher von Project Gutenberg herunterladen

Öffne **Bücher → Bücher hinzufügen → Kostenlose Bücher**. Du kannst:

- Nach Titel oder Autor suchen.
- Zwischen **Beliebt**, **Neueste** und **Zufällig** wechseln.
- Nach Sprache und Themen wie Abenteuer, Krimi, Science-Fiction, Fantasy, Lyrik oder Kinderliteratur filtern.
- Einen Titel öffnen, verfügbare Ausgaben vergleichen und Versionen mit oder ohne Bilder wählen.
- Sehen, welche Bücher bereits in der Vayana-Bibliothek vorhanden sind.
- Einen zwischengespeicherten Katalog weiter durchsuchen, wenn Project Gutenberg vorübergehend nicht verfügbar ist.

Titel von Project Gutenberg können in den USA gemeinfrei sein, aber nicht unbedingt in allen Ländern. Prüfe die Urheberrechtsregeln an deinem Wohnort.

<a id="connect-an-opds-catalog"></a>

### Einen OPDS-Katalog verbinden

Wähle **Online-Kataloge** auf dem Bildschirm Kostenlose Bücher. Füge die OPDS-Adresse eines Dienstes hinzu, etwa:

- Calibre-Inhaltsserver
- Calibre-Web
- Standard Ebooks
- Ein anderer kompatibler OPDS-Katalog

Öffne einen Katalog, um seine Navigationsfeeds zu durchsuchen, bei Serverunterstützung zu suchen und kompatible EPUB- oder PDF-Dateien herunterzuladen. Anmeldung und Verfügbarkeit hängen vom Katalogserver ab.

<p align="center">
  <img src="assets/user-guide/03-online-catalogs.png" alt="Leerer Bildschirm Online-Kataloge mit Erklärung zum Hinzufügen eines OPDS-Katalogs" width="360"><br>
  <sub>Calibre, Calibre-Web, Standard Ebooks oder einen anderen OPDS-Katalog hinzufügen.</sub>
</p>

<a id="managing-the-library"></a>

## Bibliothek verwalten

<a id="dashboard-and-filters"></a>

### Übersicht und Filter

Die Bibliothek verbindet den Katalog mit einer Leseübersicht:

- **Heute** zeigt den Fortschritt zum täglichen Leseziel und fällige Wiederholungen.
- **Weiterlesen** führt zum zuletzt aktiven, lokal verfügbaren Buch zurück.
- **Lesevorschläge** nutzt den aktuellen Bibliothekskontext, um verwandte Bücher vorzuschlagen.
- Statusfilter umfassen **Alle**, **In Arbeit**, **Abgeschlossen**, **Nicht begonnen**, **Pausiert** und **Nicht beendet**.
- Die Suche berücksichtigt Titel, Autor und Beschreibung.
- Raster-/Listenanzeige, Sortierung, Regale, Serien, Als Nächstes lesen und weitere Filter helfen bei größeren Bibliotheken.

<a id="read-next-shelves-and-series"></a>

### Als Nächstes lesen, Regale und Serien

- Füge Bücher zu **Als Nächstes lesen** hinzu und ändere die Reihenfolge.
- Erstelle Regale für persönliche Sammlungen und Filter.
- Nutze Serienmetadaten, um zusammengehörige Bücher zu gruppieren und die Reihenfolge zu erhalten.
- Ein Buch kann Tags, Bewertung, Daten, Fortschritt, Notizen und Lesestatistiken zusätzlich zu den Dateimetadaten behalten.

<a id="offline-physical-borrowed-and-other-books"></a>

### Bücher ohne digitale Datei, gedruckte, ausgeliehene und andere Bücher

Bucheinträge ohne digitale Datei benötigen kein EPUB oder PDF. Nutze sie für gedruckte Bücher, Hörbücher oder Formate, die du in einer anderen App liest. Du kannst Folgendes erfassen:

- Titel, Autor, Cover, Serie, Tags und Seitenzahl
- Lesestatus und Lesedaten
- Gelesene Seiten oder prozentualen Fortschritt
- Bewertungen, Notizen und Lesesitzungen
- Status **Eigenes Buch** oder **Ausgeliehen**
- Rückgabedatum für ausgeliehene Bücher

Bei Aktivierung können Rückgabeerinnerungen drei Tage vorher, einen Tag vorher und am Fälligkeitstag benachrichtigen.

<a id="home-library-mirror"></a>

### Home-Library-Spiegelung

Wenn die Home-Library-App des Entwicklers auf demselben Gerät installiert und die Freigabe aktiviert ist, kann Vayana deren Katalog in Bücher ohne digitale Datei spiegeln:

- Aktiviere **Mit Home Library synchronisieren** in den Einstellungen.
- Die gespiegelten Katalogfelder sind schreibgeschützt; Home Library bleibt für diese Daten verantwortlich.
- Vayana behält seinen eigenen Lesefortschritt, Daten, Status Als Nächstes lesen, Notizen und Statistiken.
- Regalstandort und gemeinsame Metadaten erscheinen in Suche und Buchdetails.
- **In Home Library anzeigen** öffnet den ursprünglichen Eintrag.

<a id="book-details-and-reading-status"></a>

## Buchdetails und Lesestatus

Die Buchdetails verbinden Metadaten und Leseaktivität. Je nach Buch zeigen sie:

- Cover, Titel, Autor, Serie, Format und Fortschritt
- Beschreibung, Verlagsangaben, Tags, Themen und eigene Metadaten
- Persönliche Bewertung und Lesestatus
- Beginn, Abschluss und Datum der letzten Lektüre
- Lesezeit und aktuellen Fortschritt
- Notizen, Markierungen, Zitate und Kapitelanzahl
- Dateiaktionen, Quellenersatz, Teilen und Coverbearbeitung
- Optionale Goodreads-Metadaten, Bewertung, Genres, Seriendaten und Community-Zitate

Änderungen an Metadaten und Cover betreffen den lokalen Vayana-Eintrag, außer bei einer schreibgeschützten Home-Library-Spiegelung.

<p align="center">
  <img src="assets/user-guide/04-book-details.png" alt="Buchdetails für Der Hund von Baskerville" width="360"><br>
  <sub>Buchdetails verbinden Metadaten, Status, Fortschritt, Daten, Notizen und Aktionen.</sub>
</p>

<a id="reading-epub-books"></a>

## EPUB-Bücher lesen

Vayanas EPUB-Reader merkt sich die Position für jedes Buch und unterstützt anpassbaren Textfluss, EPUBs mit festem Layout und Darstellungseinstellungen pro Buch.

<a id="reader-navigation"></a>

### Reader-Navigation

- Tippe oder wische zum Seitenwechsel entsprechend den eingestellten Tippzonen und dem Blätterverhalten.
- Nutze physische Lautstärke-/Seitentasten, wenn sie aktiviert sind.
- Öffne **Inhalt**, um zu einem Kapitel zu springen.
- Setze Lesezeichen und kehre zu ihnen zurück.
- Suche im aktuellen Buch.
- Sieh den Fortschritt nach Seite/Position und Prozent.
- Nutze Vollbild, Kopf-/Fußzeileninformationen und die Option zum Wachhalten des Bildschirms nach Wunsch.
- Im Querformat können breite Bildschirme zwei Spalten zeigen.

Nach einer längeren Pause kann Vayana einen kurzen **Willkommen zurück**-Rückblick mit dem letzten Kapitel und dem jüngsten Lesekontext anzeigen.

<a id="typography-and-page-appearance"></a>

### Typografie und Seitendarstellung

Öffne **Einstellungen** im Reader, um Folgendes anzupassen:

- Seitenthema: System, Hell, Papier, Sepia, Minze oder weitere verfügbare Themen
- Schriftfamilie, importierte eigene Schrift, Schriftgröße und kräftigeren Text
- Zeilenhöhe, Ausrichtung, Silbentrennung und Seitenränder
- Verlagsstile und ob Vayana die Buchtypografie überschreibt
- Inhalt von Kopf-/Fußzeile und Fortschrittsdarstellung
- Optionales bionisches Lesen
- Gespeicherte Lesevoreinstellungen und eigene Stile pro Buch

Themen können allgemein gelten, während der Schalter für eigene Stile die buchbezogenen Typografieentscheidungen bewahrt.

<table>
  <tr>
    <td width="50%" align="center">
      <img src="assets/user-guide/06-reader.png" alt="EPUB-Reader mit einem gemeinfreien Sherlock-Holmes-Kapitel in dunklem Thema" width="360"><br>
      <sub><strong>Reader</strong>: ablenkungsfreier Text mit Fortschritt und Rückblick bei der Rückkehr.</sub>
    </td>
    <td width="50%" align="center">
      <img src="assets/user-guide/07-reader-controls.png" alt="Reader-Steuerung und Typografieeinstellungen" width="360"><br>
      <sub><strong>Reader-Steuerung</strong>: Inhalt, Notizen, Fortschritt, Stil, Vorlesen und Suche.</sub>
    </td>
  </tr>
</table>

<a id="reading-pdf-books"></a>

## PDF-Bücher lesen

PDF-Dateien lassen sich über Dateiauswahl, Ordnersuche, Öffnen mit, Teilen oder Quelle ersetzen importieren.

Der PDF-Reader unterstützt:

- Eine Seite pro Bildschirm mit Seitenwechsel durch Tippen und physische Tasten
- PDF-Gliederungs-/Inhaltsnavigation, sofern das Dokument sie bereitstellt
- Navigation nach Seitennummer und gespeicherte Position
- An Seite anpassen, an Breite anpassen, Zoom und Verschieben
- Randsteuerung für Helligkeit/warmes Licht, soweit konfiguriert
- Lesezeichen in allen PDFs
- Textauswahl, Kopieren, Wörterbuch, Markierungen, Unterstreichungen, Notizen und Zitatkarten bei PDFs mit Textebene
- Covererstellung aus der ersten Seite sowie Titel-/Autorenübernahme aus vorhandenen PDF-Metadaten

Gescannte Bild-PDFs ohne Textebene lassen sich anzeigen, vergrößern, verschieben und mit Lesezeichen versehen, aber ihr Text kann nicht ausgewählt oder durchsucht werden. PDF-Schriften und -Abstände sind Teil der Seite; EPUB-Typografieeinstellungen gelten daher nicht.

<a id="highlights-notes-bookmarks-and-quotes"></a>

## Markierungen, Notizen, Lesezeichen und Zitate

<a id="while-reading"></a>

### Beim Lesen

Wähle Text aus, um Aktionen wie diese aufzurufen:

- Markieren oder unterstreichen
- Eine Notiz hinzufügen/bearbeiten
- Ausgewählten Text kopieren oder teilen
- Eine visuelle Zitatkarte erstellen
- Die Auswahl definieren, suchen oder übersetzen
- Anmerkungs-Tags hinzufügen

Überlappende EPUB-Markierungen können zusammengeführt werden. Fußnoten können als Pop-ups erscheinen, wenn das Buch dies unterstützt.

<a id="notes-library"></a>

### Notizbibliothek

Der globale Bereich **Notizen** gruppiert Anmerkungen nach Buch und zeigt Anzahl und Aktualisierungsdaten. Er unterstützt:

- Suche in Notizen, Büchern und markiertem Text
- Filter und direkte Navigation zur ursprünglichen Textstelle
- Bearbeiten und Teilen von Anmerkungen
- Markdown-Export
- Automatische Markdown-Notizbücher, sofern konfiguriert
- Kindle-Import von `My Clippings.txt`
- Goodreads-Community-Zitatimport, getrennt von persönlichen Anmerkungen
- Wiederholung von Markierungen in zeitlichen Abständen mit **Bald wiedersehen**, **Verstanden** und **Gut bekannt**

<p align="center">
  <img src="assets/user-guide/08-notes.png" alt="Nach gemeinfreien Büchern gruppierte Notizbibliothek" width="360"><br>
  <sub>Notizen werden nach Buch mit Anmerkungs- und Kapitelanzahl gruppiert.</sub>
</p>

<a id="dictionary-translation-and-vocabulary"></a>

## Wörterbuch, Übersetzung und Vokabeln

- Tippe zweimal auf ein Wort in einer unterstützten Textansicht, um das mitgelieferte Offline-Wörterbuch zu öffnen.
- Vayana nutzt die Buchsprache zum Nachschlagen, wenn entsprechende Metadaten vorhanden sind.
- Suche ausgewählte Wörter oder Wendungen in Wiktionary oder Wikipedia, wenn Internet verfügbar ist.
- Sende eine Auswahl an eine installierte Übersetzungs-App.
- Speichere nachgeschlagene Wörter in der Vokabelliste.
- Entdecke ungewöhnliche Wörter aus dem aktuellen Kapitel.
- Markiere vertraute Begriffe als bekannt.
- Wiederhole gespeicherte Vokabeln in zeitlichen Abständen.
- Exportiere Vokabeln als Anki-kompatible CSV oder lesbares Markdown.

Online-Nachschlagen sendet nur die ausgewählte Anfrage an den gewählten Anbieter, wenn du es anforderst. Übersetzung benötigt eine kompatible Übersetzungs-App.

<a id="read-aloud"></a>

## Vorlesen

Android-Sprachausgabe kann ein EPUB Satz für Satz lesen und zwischen Kapiteln fortfahren.

- Beginne an der aktuellen Position oder einer ausgewählten Textstelle.
- Verfolge die Markierung des gesprochenen Wortes/Satzes im Text.
- Wähle eine installierte TTS-Engine, Sprache, Stimme, Geschwindigkeit und Tonhöhe.
- Nutze einen Abschalttimer.
- Pausiere durch Tippen in den Reader.
- Steuere die Wiedergabe über Benachrichtigungen, Sperrbildschirm, Kopfhörer oder Bluetooth.
- Audiofokus pausiert die Wiedergabe oder senkt ihre Lautstärke, wenn eine andere App Audio benötigt.
- Das Weiterlesen-Widget kann das Vorlesen des angezeigten Buches starten oder pausieren.

Verfügbare Stimmen und Sprachen hängen von den auf dem Gerät installierten TTS-Engines ab. Vorlesen ist hauptsächlich für anpassbaren Textfluss gedacht, nicht für gescannte PDFs.

<a id="search"></a>

## Suche

Die globale Suche findet:

- Titel, Autoren, Beschreibungen, Serien, Tags und weitere Metadaten
- EPUB-Kapitelnamen und indexierten Buchtext
- Persönliche Markierungen und Notizen
- Letzte Suchanfragen und Präfixtreffer

Lokal verfügbare EPUB-Inhalte werden für schnelle Suche indexiert. PDF-Seitentext und Inhalte gedruckter Bücher werden nicht indexiert, ihre persönlichen Anmerkungen können jedoch in der Anmerkungssuche erscheinen.

<a id="statistics-and-goals"></a>

## Statistiken und Ziele

**Reading records** zeigt Bücher mit gespeicherter Leseaktivität, auch gedruckte Bücher, die noch gelesen werden. Jeder Eintrag enthält Cover, Titel, Autor, gespeicherte Start- und Abschlussdaten sowie die gesamte erfasste Lesezeit. Tippe auf einen Eintrag, um die Buchdetails zu öffnen; **View all reading records** zeigt bei mehr als drei Büchern die vollständige Liste. Unbekannte Daten erscheinen als **Not recorded**. Die Zeit stammt aus gespeicherten Sitzungen; ein Abschlussdatum allein schätzt keine Lesezeit.

Vayana erfasst Lesesitzungen und zeigt:

- Tägliche und gesamte Lesezeit
- Leseserien und Aktivität in Kalenderform
- Abgeschlossene Bücher nach Monat und Jahr
- Tägliche Minutenziele und jährliche Buchziele
- Leseverlauf pro Buch
- Speicherverbrauch für Bücher, Cover, Notizen/Zitate, Wörterbuchdaten und Cache
- Summen nach Format sowie größte/kleinste Bücher

Statistiken basieren auf von Vayana erfasster Aktivität. Von einem anderen Gerät synchronisierte Sitzungen können nach der Synchronisierung beitragen.

<p align="center">
  <img src="assets/user-guide/05-statistics.png" alt="Statistikbildschirm mit Leseaktivität, Zielen und Speicherverbrauch" width="360"><br>
  <sub>Leseaktivität, Ziele, Bibliothekssummen und Speicherverbrauch.</sub>
</p>

<a id="wear-os-companion"></a>

## Wear-OS-Begleit-App

Die Begleit-App erfasst das Lesen gedruckter Bücher auf Wear OS 3+ mit Google Play-Diensten und einem gekoppelten Android-Smartphone. Richte sie mit passenden aktuellen Smartphone- und Uhr-Builds gemäß der [Wear-OS-Anleitung](WEAR_OS.md#install-and-build) ein.

1. Füge auf dem Smartphone ein gedrucktes Buch hinzu und markiere es als aktuelle Lektüre.
2. Öffne Vayana auf der Uhr und wähle **Mit Smartphone synchronisieren** (Sync with phone), um deine Bücher zwischenzuspeichern.
3. Wähle ein Buch, prüfe die Startseite und tippe auf **Zeitmessung starten** (Start timer). Auf der Uhr gestartete Zeitmesser, Pausen und Seitenänderungen funktionieren offline.
4. Tippe auf **Stoppen** (Stop), gib die Endseite ein und wähle **Speichern** (Save). Abbrechen lässt einen auf der Uhr gestarteten Zeitmesser pausiert.
5. Verbinde die Geräte wieder, um gespeicherte Sitzungen an Verlauf und Statistiken des Smartphones zu senden. Ausstehende Sitzungen bleiben bis zur Bestätigung auf der Uhr.

Ein laufender Zeitmesser auf dem Smartphone kann auch auf der Uhr erscheinen. Dort kannst du ihn pausieren, fortsetzen, stoppen und seine Seite ändern. Fernaktionen warten auf die Bestätigung des zuständigen Geräts; eine Offline-Anzeige bestätigt nicht, dass ein Befehl angewendet wurde. Das Smartphone bietet ebenfalls Steuerelemente für einen auf der Uhr gestarteten Zeitmesser.

Nutze **Leseziele** (Reading goals) für ein Tagesziel oder eine Sitzungserinnerung und **Vibration** für haptische Rückmeldung. Die Begleit-App enthält eine Ambient-Anzeige, eine Lesekachel und eine Zifferblatt-Komplikation. Informationen zu Seitenkonflikten, überlappenden Sitzungen, Wiederherstellung nach Neustart und Verbindungsanzeigen findest du in der [vollständigen Begleit-App-Anleitung](WEAR_OS.md).

Die Uhr öffnet keine E-Book-Dateien. Ihre Begleitverbindung nutzt Googles Data Layer unabhängig von der optionalen GitHub-Synchronisierung.

<a id="widgets-and-app-shortcuts"></a>

## Widgets und App-Verknüpfungen

Vayana bietet zwei konfigurierbare Startbildschirm-Widgets:

- **Weiterlesen** zeigt das aktuelle Buch, Cover, Fortschritt und eine Rückkehr zum Lesen mit einem Tippen. Bei aktiviertem Vorlesen kann es Wiedergabe/Pause anzeigen.
- **Lesezeit** zeigt die heutigen Minuten und ein anpassbares Sieben-Tage-Diagramm mit Durchschnitt.

Die Widget-Konfiguration zeigt eine Vorschau der Änderungen und wendet gemeinsame Einstellungen für Eckenradius und Fortschrittsstil auf Vayana-Widgets an. Das Layout passt sich der im Launcher gewählten Größe an.

Halte das Vayana-Symbol im Launcher gedrückt, um folgende Verknüpfungen aufzurufen:

- Kostenlose Bücher
- Suche
- Statistiken

<a id="backup-and-restore"></a>

## Sicherung und Wiederherstellung

<a id="manual-backup"></a>

### Manuelle Sicherung

Öffne **Einstellungen → Sicherung und Wiederherstellung**, um ein übertragbares ZIP mit Bibliotheksdatenbank, Einstellungen, von Vayana verwalteten Buchdateien, Covern und zugehörigen Lesedaten zu erstellen.

<a id="automatic-backup"></a>

### Automatische Sicherung

Wähle einen Ordner, eine Häufigkeit und die Anzahl aufzubewahrender Sicherungen. Verfügbar sind täglich, alle sieben Tage und alle 30 Tage. Android kann Hintergrundarbeit je nach Akku- und Gerätebeschränkungen verzögern.

<a id="restore-safely"></a>

### Sicher wiederherstellen

Vayana zeigt vor der Wiederherstellung eine Zusammenfassung mit Erstellungsdatum, App-Version, Buchanzahl, Anmerkungsanzahl und Größe. Die Wiederherstellung ersetzt die aktuelle Bibliothek und Einstellungen auf dem Gerät und startet die App neu. Erstelle vorher eine neue Sicherung, wenn du die aktuelle Bibliothek noch benötigen könntest.

Sicherung ist von GitHub-Synchronisierung getrennt: Eine Sicherung ist ein übertragbares Archiv eines bestimmten Zeitpunkts; Synchronisierung führt unterstützte Daten zwischen konfigurierten Geräten zusammen.

<a id="github-sync"></a>

## GitHub-Synchronisierung

Die GitHub-Synchronisierung nutzt ein Repository unter deiner Kontrolle. Sie kann Folgendes synchronisieren:

- Bücher, Cover und Bibliotheksmetadaten
- Leseposition, Sitzungen und Daten
- Markierungen, Notizen, Regale und Als Nächstes lesen
- Vokabeln und übertragbare Einstellungen
- Löschungen und Wiederherstellungen

Buch- und Coverdateien werden vor dem Hochladen mit AES-GCM und der Synchronisierungs-Passphrase verschlüsselt. Das GitHub-Token wird auf dem Gerät gespeichert. Leichte Fortschrittssynchronisierung kann während des Lesens laufen, während die vollständige Synchronisierung Bibliothekszustand und Dateien abgleicht.

Wichtige Einrichtungsregeln:

- Nutze ein privates Repository nur für die Vayana-Synchronisierung.
- Gib dem Token nur den benötigten Repository-Zugriff.
- Nutze auf allen Geräten dasselbe Repository, denselben Branch und dieselbe Passphrase.
- Gib jedem Gerät einen erkennbaren Namen für Konflikt- und Verlaufsmeldungen.
- Bewahre die Passphrase sicher auf; verschlüsselte Dateien lassen sich ohne sie nicht wiederherstellen.
- Nutze **GitHub-Verbindung testen** vor der ersten vollständigen Synchronisierung.

Siehe [GitHub-Synchronisierung einrichten](GITHUB_SYNC_SETUP.md) für die vollständige Anleitung und Prüfliste zur Fehlerbehebung.

<a id="e-ink-and-accessibility"></a>

## E-Ink und Barrierefreiheit

<a id="e-ink-display-profile"></a>

### E-Ink-Anzeigeprofil

E-Ink ist ein eigenes Profil statt eines einfachen Graustufenthemas. Es kann:

- Bewegung und Umblätteranimationen entfernen oder reduzieren
- Statischen Fortschritt anzeigen
- **Monochrome** und kontrastreiche **Farbpaletten** unterstützen
- Reader-Aktualisierungen möglichst an Seitenwechsel koppeln
- Physische Seitentasten nutzen
- Steuerelemente zum Weiterblättern um Bildschirmhöhen auf langen App-Bildschirmen hinzufügen
- Bereinigende Reader-Aktualisierungen bei eingestellten Intervallen und Übergängen anfordern
- Kräftigeren Text, Ausrichtung und Silbentrennung anbieten

Herstellerspezifische Aktualisierungs-APIs sind derzeit nicht integriert. Das Verhalten hängt deshalb vom Android-Gerät und dessen eigenem Aktualisierungsmodus ab.

<a id="accessibility-and-adaptive-layout"></a>

### Barrierefreiheit und adaptives Layout

- Material-3-Komponenten bieten Inhaltsbeschreibungen und skalierbaren Text.
- Bewegung kann Vollständig, Reduziert oder Aus sein.
- Standarddarstellung, sanfter Dunkelmodus und echtes Schwarz stehen zur Verfügung.
- Smartphone- und Tablet-Layouts passen Navigation und Inhaltsbreite an.
- Reader-Text, Ränder, Kontrast und Zeilenabstand lassen sich unabhängig einstellen.

<a id="settings-and-languages"></a>

## Einstellungen und Sprachen

Die Einstellungen sind gruppiert und durchsuchbar:

- **Darstellung:** Thema, Farben, E-Ink, Bewegung, Navigation, Daten und App-Sprache
- **Bibliothek:** Startbildschirm, Cover, Verhalten beim Abschluss, Bücher ohne digitale Datei, Home Library, Erinnerungen und Kürzlich gelöscht
- **Reader-Text:** Schrift, Größe, Abstände, Buchstile und eigene Schriften
- **Reader-Seite:** Seitenfarben, Ränder, Kopfzeile, Fußzeile und Fortschrittsanzeige
- **Reader-Steuerung:** Tippen, Tasten, Markierungen, Wachhalten, Vorlesen und zugehörige Steuerung
- **Leseziele:** tägliche Minuten, jährliche Bücher und Wochenbeginn
- **Synchronisierung:** GitHub-Repository, Zugangsdaten, Passphrase, Gerätename, Zustand, Tests und Übertragung
- **Sicherung und Wiederherstellung:** manuelle und geplante Sicherungen
- **Hilfe und Über:** integrierte Hilfe, Diagnose, Version, Quellcode, Versionslink, Teilen und Entwicklerlinks

Vayanas Oberflächensprache kann unabhängig von der Buchsprache gewählt werden. Version 0.90 enthält Englisch, Spanisch, Portugiesisch, Russisch, Deutsch, Französisch, Italienisch, Malayalam und Tamil. Suche in den Einstellungen nach **Sprache**, um die App-Sprache zu ändern; Bücher behalten ihre eigenen Sprachmetadaten.

<p align="center">
  <img src="assets/user-guide/11-settings.png" alt="Durchsuchbare Einstellungskategorien" width="360"><br>
  <sub>Durchsuchbare Einstellungsgruppen trennen globale und readerspezifische Optionen.</sub>
</p>

<a id="diagnostics-and-support"></a>

## Diagnose und Support

Öffne **Einstellungen → Hilfe und Über → Diagnose**, wenn die App abstürzt oder die Synchronisierung unerwartet reagiert.

Diagnose zeichnet jüngste unbehandelte Abstürze und Synchronisierungsprobleme lokal auf. Der Bildschirm zeigt Anzahlen, neueste Ereignisse zuerst und ausklappbare technische Details. Außerdem bietet er:

- **Mit Entwickler teilen**, um das Android-Teilen-Menü mit einem Klartextbericht zu öffnen
- **Bericht kopieren**, um den Bericht in die Zwischenablage zu legen
- **Alle Protokolle löschen**, um gespeicherte Diagnoseereignisse vom Gerät zu entfernen

Der erzeugte Bericht enthält App-Version, Android-Version, Gerätemodell, Ereigniszeiten, Quellen, Meldungen und technische Ablaufspuren. Er enthält keine privaten Buchinhalte, Notizen oder Einstellungen. Übliche Autorisierungswerte, geheime URL-Parameter, GitHub-Token-Muster, Benutzerpfade, externe Dateipfade, Content-URIs und E-Mail-Adressen werden geschwärzt. Prüfe den Bericht immer vor dem Teilen.

Gib bei einem Problembericht Folgendes an:

- Was du gerade getan hast
- Was du erwartet hast
- Was stattdessen passiert ist
- Ob es jedes Mal passiert
- Buchformat und ob ein oder alle Bücher betroffen sind
- Den Diagnosebericht, falls relevant

Veröffentliche niemals ein GitHub-Token oder die Synchronisierungs-Passphrase in einem Issue.

<table>
  <tr>
    <td width="50%" align="center">
      <img src="assets/user-guide/10-diagnostics.png" alt="Diagnosebildschirm mit Teilen- und Kopieraktionen" width="360"><br>
      <sub><strong>Diagnose</strong>: bereinigte Absturz- oder Synchronisierungsinformationen prüfen und teilen.</sub>
    </td>
    <td width="50%" align="center">
      <img src="assets/user-guide/09-about.png" alt="Vayana-Über-Bildschirm mit Version, Quellcode, Veröffentlichungen, Support und weiteren Apps" width="360"><br>
      <sub><strong>Über</strong>: Version, Quellcode, Veröffentlichungen, Unterstützung, Teilen und Entwicklerlinks.</sub>
    </td>
  </tr>
</table>

Melde reproduzierbare Probleme über [GitHub Issues](https://github.com/rjwarrier/Vayana/issues).

<a id="privacy-and-online-services"></a>

## Datenschutz und Online-Dienste

Grundlegendes Lesen, lokale Suche, Notizen, Offline-Wörterbuch, Statistiken und lokale Sicherung funktionieren ohne Vayana-Konto.

Netzwerkzugriff erfolgt, wenn du eine Online-Funktion auswählst oder einrichtest:

| Funktion | Betroffene Daten |
| --- | --- |
| Project Gutenberg | Such-/Filteranfragen und Buchdownloads |
| OPDS | Katalogadresse, Navigations-/Suchanfragen, optionale Serveranmeldung und Downloads |
| Goodreads | Abfragen zu Buchmetadaten/Covern und Browseralternative |
| Wiktionary/Wikipedia | Ausgewähltes Wort oder Wendung beim angeforderten Nachschlagen |
| Übersetzung | Ausgewählter Text an die gewählte Übersetzungs-App bzw. den Anbieter |
| GitHub-Synchronisierung | Synchronisierungsmetadaten sowie AES-GCM-verschlüsselte Buch-/Coverdateien; Repository-Kennungen und Token bleiben in den App-Einstellungen auf dem Gerät |
| Coversuche | Die für die ausgewählte Quelle benötigten Buchsuchmetadaten |

Diagnoseprotokolle bleiben auf dem Gerät, bis du sie ausdrücklich kopierst, teilst oder löschst. Die ausgewählte empfangende Android-App bestimmt, was nach dem Teilen passiert.

<a id="troubleshooting"></a>

## Fehlerbehebung

<a id="a-book-will-not-import"></a>

### Ein Buch lässt sich nicht importieren

- Prüfe, ob es ein gültiges EPUB oder PDF ist und ein anderer Reader es öffnen kann.
- Passwortgeschützte PDFs werden nicht unterstützt.
- Falls Android einen allgemeinen Dateityp meldet, behalte die Erweiterung `.epub` oder `.pdf`.
- Probiere Vayanas Dateiauswahl statt Teilen/Öffnen mit.
- Prüfe freien Speicher und erteile Zugriff auf die ausgewählte Datei bzw. den Ordner.

<a id="a-book-is-missing-after-moving-files"></a>

### Ein Buch fehlt nach dem Verschieben von Dateien

- Öffne die Buchdetails und nutze **Quelle ersetzen**, um den Eintrag neu zu verbinden.
- Falls es dauerhaft aus Vayana gelöscht wurde, importiere die Datei erneut.
- Prüfe **Kürzlich gelöscht**, wenn es vor Kurzem entfernt wurde, und stelle es dort wieder her.

<a id="project-gutenberg-or-opds-does-not-load"></a>

### Project Gutenberg oder OPDS lädt nicht

- Prüfe die Internetverbindung und versuche es erneut.
- Project Gutenberg kann langsam antworten; Vayana kann offline den gespeicherten Katalog anzeigen.
- Prüfe bei OPDS die genaue Katalog-URL und Erreichbarkeit des Servers.
- Prüfe bei Anmeldefehlern die Serverzugangsdaten außerhalb von Vayana.

<a id="dictionary-or-translation-is-unavailable"></a>

### Wörterbuch oder Übersetzung ist nicht verfügbar

- Prüfe, ob die Buchsprache in den Metadaten korrekt ist.
- Offline-Ergebnisse hängen von den installierten/mitgelieferten Wörterbuchdaten ab.
- Die Wikimedia-Alternative benötigt Internetzugriff.
- Übersetzung benötigt eine kompatible App.

<a id="read-aloud-has-no-voice"></a>

### Beim Vorlesen fehlt eine Stimme

- Installiere oder aktiviere eine Android-TTS-Engine und die benötigte Sprachstimme.
- Prüfe Medienlautstärke und Bluetooth-Ausgabe.
- Prüfe Engine, Stimme, Sprache, Geschwindigkeit und Tonhöhe in den Reader-/Audioeinstellungen.
- Vergewissere dich auf E-Ink-Geräten, dass **Audiofunktionen** aktiviert sind.

<a id="github-sync-fails"></a>

### GitHub-Synchronisierung schlägt fehl

- Öffne **Einstellungen → Synchronisierung** und führe **GitHub-Verbindung testen** aus.
- Prüfe Eigentümer, Repository, Branch, Token und Passphrase.
- Stelle sicher, dass das Token Zugriff auf das konfigurierte Repository hat.
- Nutze dieselbe Passphrase auf allen Geräten.
- Öffne Diagnose und teile das bereinigte Synchronisierungsereignis, wenn der Fehler unklar bleibt.

<a id="statistics-look-incomplete"></a>

### Statistiken wirken unvollständig

- Nur von Vayana erfasste Sitzungen werden gezählt.
- Prüfe, ob das Buch über Vayana geöffnet wird und die Geräteuhr korrekt ist.
- Synchronisiere, wenn die Aktivität auf einem anderen konfigurierten Gerät erfasst wurde.

<a id="the-app-crashed"></a>

### Die App ist abgestürzt

1. Öffne Vayana erneut.
2. Gehe zu **Einstellungen → Hilfe und Über → Diagnose**.
3. Klappe den neuesten Absturz aus und prüfe, ob die Uhrzeit passt.
4. Nutze **Mit Entwickler teilen** oder **Bericht kopieren**.
5. Beschreibe die Aktion unmittelbar vor dem Absturz.

<a id="current-limitations"></a>

## Aktuelle Einschränkungen

- Unterstützte lesbare digitale Formate sind EPUB und PDF.
- Gescannte PDFs ohne Textebene bieten keine Auswahl, Nachschlagen, Suche oder Textanmerkungen.
- Die PDF-Typografie wird vom Dokument festgelegt; EPUB-Schrift- und Abstandseinstellungen gelten nicht.
- PDF-Unterstützung umfasst derzeit nicht alle EPUB-exklusiven Funktionen, etwa bionisches Lesen oder Community-Zitate.
- Passwortgeschützte PDFs werden nicht unterstützt.
- Inhalte gedruckter Bücher und PDF-Seitentext gehören nicht zur vollständigen Buchindexierung.
- Herstellerspezifische E-Ink-Aktualisierungs-SDKs sind nicht integriert.
- Der Git-Verlauf kann nach einer dauerhaften Cloud-Löschung verschlüsselte Dateien in älteren Synchronisierungs-Commits behalten, solange der Repository-Verlauf nicht neu geschrieben wird.
- Online-Kataloge, Anreicherung, Nachschlagen, Übersetzung und Synchronisierung hängen von der Verfügbarkeit externer Dienste ab.

<a id="related-documentation"></a>

## Weiterführende Dokumentation

- [Funktionsverhalten und Implementierungsdetails](FEATURES.md)
- [GitHub-Synchronisierung einrichten](GITHUB_SYNC_SETUP.md)
- [Architektur- und Implementierungsentscheidungen](DECISIONS.md)
- [Datenbank-Änderungsverlauf](DATABASE_CHANGELOG.md)
- [Vayana-Version 0.90](https://github.com/rjwarrier/Vayana/releases/tag/v0.90)
