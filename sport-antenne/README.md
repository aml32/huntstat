# Sport Antenne

Appli Android personnelle : les prochaines retransmissions sportives à la télé française
et la chaîne qui les diffuse. Sans pub, sans compte, sans achat.

- **Programme** : tout, mes favoris ou en ce moment, par sport ; le passé est masqué.
- **Favoris** : équipes/joueurs et compétitions à cocher (avec recherche), sports à cocher
  pour ne garder que ceux-là dans le programme.
- **Chaînes** : cocher les chaînes reçues pour masquer les autres.
- **Alertes** : notification avant chaque diffusion d'un favori (délai réglable), ou match par match.
- Ajout au calendrier avec la chaîne dans le titre, widget d'accueil.

Données : guide XMLTV public de [xmltvfr.fr](https://xmltvfr.fr) (≈ 5 jours de programmes),
téléchargé à l'ouverture puis environ toutes les 4 heures. Le sport, la compétition et les
équipes sont déduits des titres du guide (`core/XmltvParser.kt`). Une rediffusion est un match
déjà diffusé plus tôt, ou un match européen diffusé en pleine nuit (`core/Model.kt`).

`AppSmokeTest` lance la vraie activité sur un guide réel et parcourt tous les écrans :
`GUIDE_GZ=/chemin/xmltv_fr.xml.gz SHOTS=/dossier ./gradlew testDebugUnitTest`.

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
