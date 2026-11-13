# Sport Antenne

Appli Android personnelle : les prochaines retransmissions sportives à la télé française
et la chaîne qui les diffuse. Sans pub, sans compte, sans achat.

- **À venir** : un jour par page (les jours côte à côte, on glisse pour changer), uniquement les
  directs, avec la chaîne. Sports à cocher en haut : le choix est mémorisé et filtre le programme.
- **Favoris** : équipes/joueurs et compétitions à cocher (avec recherche), sports à cocher.
- **Chaînes** : cocher les chaînes reçues pour masquer les autres.
- **Alertes** : notification avant chaque diffusion d'un favori (délai réglable), ou match par match.
- Ajout au calendrier avec la chaîne dans le titre, widget d'accueil.

## Données et fiabilité

Deux familles de sources, recoupées à chaque mise à jour (ouverture, puis ~toutes les 4 h) :

1. **Guide TV** XMLTV de [xmltvfr.fr](https://xmltvfr.fr) : ce qui passe sur quelle chaîne (≈ 5 jours).
   Sport, compétition et équipes sont déduits des titres (`core/XmltvParser.kt`).
2. **Calendriers réels** (`core/Fixtures.kt`) : tableau des scores public d'ESPN (football toutes
   compétitions, NBA, NFL, NHL, rugby) et Jolpica/Ergast (toutes les séances de Formule 1).

`core/LiveCheck.kt` compare chaque diffusion aux calendriers : un match qui démarre réellement
pendant la diffusion est un **direct confirmé** ; une compétition couverte sans match à cette
heure-là est une **rediffusion** (masquée). Pour les sports sans calendrier (tennis, golf, handball…),
des indices du guide servent de repli (`Schedule.guessReplay` dans `core/Model.kt`) ; le réglage
« Directs confirmés uniquement » les masque.

Tests : `XmltvParserTest` (analyse), `AppSmokeTest` (lance la vraie activité sur un guide réel et
parcourt les écrans), `LiveSourcesTest` (interroge les calendriers en ligne) :

```
GUIDE_GZ=/chemin/xmltv_fr.xml.gz SHOTS=/dossier LIVE_SOURCES=1 ./gradlew testDebugUnitTest
```

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
