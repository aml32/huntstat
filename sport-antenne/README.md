# Sport Antenne

Appli Android personnelle : les prochaines retransmissions sportives à la télé française
et la chaîne qui les diffuse. Sans pub, sans compte, sans achat.

- **À venir** : événements en cours et à venir, filtrables par sport (le passé est masqué).
- **Favoris** : sports, compétitions, équipes et joueurs suivis.
- **Explorer** : sports → compétitions → diffusions, recherche d'équipe / joueur / compétition.
- **Réglages** : chaînes reçues, rediffusions, magazines.
- Widget d'accueil et ajout d'un événement à l'agenda.

Données : guide XMLTV public de [xmltvfr.fr](https://xmltvfr.fr) (≈ 5 jours de programmes),
téléchargé à l'ouverture puis toutes les 6 heures. Le sport, la compétition et les équipes
sont déduits des titres du guide (`core/XmltvParser.kt`) ; une rediffusion est un match déjà
diffusé plus tôt dans le guide.

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
