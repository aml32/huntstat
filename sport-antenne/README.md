# Sport Antenne

Appli Android personnelle : les prochaines retransmissions sportives à la télé française
et la chaîne qui les diffuse. Sans pub, sans compte, sans achat.

- **À venir** : un jour par page (jours côte à côte, le jour affiché en surbrillance), uniquement les
  directs, avec le logo et le nom de la chaîne. Onglets de sport : seulement les sports cochés en favoris.
- **Favoris** : sports à cocher, puis pour chaque sport ses compétitions classées par pays
  (`core/Competitions.kt`). Sans compétition cochée, tout le sport est affiché ; sinon seules les
  compétitions cochées. Les compétitions vues dans le guide mais hors catalogue restent cochables.
  Le catalogue compte environ 370 compétitions dans 45 sports ; un test vérifie que chacune est
  reconnue sous son propre nom, et le rapport de `XmltvParserTest` liste les libellés du guide réel
  qui ne sont rattachés à aucune (section « HORS CATALOGUE »).
- **Logos** : chaînes (adresse fournie par le guide TV, pastille de couleur à défaut) et écussons
  des équipes pour les directs confirmés (fournis par le calendrier).
- **Détail des épreuves** (`core/Races.kt`) : hommes / femmes / mixte, format (biathlon : sprint,
  poursuite, individuel, mass start, relais, relais mixte, relais mixte simple ; ski, cyclisme,
  sports mécaniques, tours de tournoi), distance et lieu, lus dans les libellés du guide.
  Le biathlon n'a pas de calendrier indépendant : le serveur de l'IBU exige une authentification.
- **Plusieurs chaînes** : toutes les chaînes d'une même diffusion sont listées, une par ligne.
- **Scores** : pendant un match confirmé, le score et la minute de jeu sont relus toutes les minutes
  tant que l'appli est à l'écran ; à la fin, le résultat reste affiché. Le réglage « Matchs terminés »
  fixe combien de temps (dès la fin, 1 h, 4 h, 12 h, tous).
- **Logos de chaînes** (`core/ChannelLogos.kt`) : table de logos vérifiés un par un dans le dépôt public
  tv-logo/tv-logos, puis logo de la marque pour les déclinaisons, puis adresse fournie par le guide ;
  si rien ne répond, le nom de la chaîne est écrit dans le cadre.
- **Chaînes** : cocher les chaînes reçues pour masquer les autres.
- **Alertes** : notification avant chaque direct d'une compétition cochée (délai réglable), ou match par match.
- Ajout au calendrier avec la chaîne dans le titre, widget d'accueil.

## Données et fiabilité

Deux familles de sources, recoupées **toutes les heures** (tâche de fond) et à chaque retour
dans l'appli après plus d'une heure. Le guide TV n'est retéléchargé que s'il a changé (requête
conditionnelle `If-None-Match`) ; les calendriers, légers, sont relus à chaque fois pour suivre
reports, annulations et changements d'horaire.

1. **Guide TV** XMLTV de [xmltvfr.fr](https://xmltvfr.fr) : ce qui passe sur quelle chaîne (≈ 5 jours).
   Sport, compétition et équipes sont déduits des titres (`core/XmltvParser.kt`).
2. **Calendriers réels** (`core/Fixtures.kt`) : tableau des scores public d'ESPN (football toutes
   compétitions, NBA, NFL, NHL, rugby) et Jolpica/Ergast (toutes les séances de Formule 1).

Les mêmes tableaux d'ESPN donnent aussi les tournois de tennis (ATP, WTA) et de golf (PGA, circuit
européen, LPGA) avec leurs dates, les soirées UFC, et le basket et le football universitaires
américains. Un tournoi est confirmé au jour près (« tournoi en cours confirmé »), un match à l'heure près.
Les réglages affichent ce que chaque calendrier a réellement fourni.

**Deuxième source** : TheSportsDB (clé publique gratuite) donne les journées complètes de rugby
(Top 14, Pro D2, Nations Championship, Six Nations, Champions Cup, URC, Premiership), handball
(Starligue, Ligue des champions, Ligue européenne), basket (Betclic Élite, Euroligue, LFB), volley
(Ligue A), hockey (Ligue Magnus) et les étapes de la Coupe du monde de biathlon avec l'heure de
départ de chaque épreuve. L'accès gratuit est limité en débit : la lecture complète prend deux à
trois minutes et n'est refaite que toutes les six heures, en arrière-plan. Pour un usage à plusieurs,
il faut une clé payante.

`core/LiveCheck.kt` compare chaque diffusion aux calendriers : un match qui démarre réellement
pendant la diffusion est un **direct confirmé** ; une compétition couverte sans match à cette
heure-là est une **rediffusion** (masquée). Pour les sports sans calendrier (tennis, golf, handball…),
des indices du guide servent de repli (`Schedule.guessReplay` dans `core/Model.kt`) ; le réglage
« Directs confirmés uniquement » les masque.

## Diagnostic

Réglages > « Envoyer le diagnostic » produit un rapport texte (version, calendriers chargés ou en échec
avec la raison, nombre de directs confirmés, non confirmés et rediffusions, réglages actifs), partagé
et copié dans le presse-papiers. Les réglages affichent aussi l'état de chaque calendrier.

## Tests

90 tests automatiques (`./gradlew testDebugUnitTest`) :

- `XmltvParserTest` — lecture du guide TV ; `LiveCheckTest` — directs, rediffusions, reports,
  annulations, noms d'équipes français/anglais, hommes/femmes/jeunes ; `CompetitionsTest` — catalogue par pays ;
  `SettingsTest` — favoris, filtres, alertes.
- `AppFlowsTest` — l'appli est lancée et manipulée comme par un utilisateur : cocher/décocher sports,
  compétitions et chaînes, alertes (programmation, délai, annulation, notification unique),
  calendrier, persistance, fichiers de cache corrompus.
- `AppSmokeTest` — parcours de tous les écrans sur un guide réel (`GUIDE_GZ=…`, captures dans `SHOTS=…`).
- `LiveSourcesTest`, `RefreshLiveTest` — vraies sources en ligne et mise à jour complète (`LIVE_SOURCES=1`).

## Installer

`dist/sport-antenne.apk` : copier sur le téléphone, ouvrir, autoriser l'installation
depuis cette source.

## Compiler

```
echo "sdk.dir=/chemin/vers/android-sdk" > local.properties
./gradlew testDebugUnitTest assembleRelease
```

L'APK « release » est signé avec la clé de debug de la machine : un APK compilé ailleurs
demande de désinstaller l'appli avant de le réinstaller.
