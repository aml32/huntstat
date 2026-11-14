# Serveur agrégateur Sport Antenne

Équivalent du backend que l'appli de référence héberge sur son serveur : il interroge **une seule
fois**, de façon centralisée, toutes les sources publiques, applique la même logique de vérification
que l'appli (module `core` partagé), et produit un unique `feed.json` que les téléphones téléchargent.

Les milliers de téléphones ne tapent plus les sources directement : ils lisent un fichier léger
(~0,6 Mo compressé) servi par le CDN de GitHub. Une correction se fait côté serveur, sans republier l'appli.

## Sources agrégées

- **Chaînes et programme** : guide XMLTV public `xmltvfr.fr`.
- **Calendriers des matchs et scores** : ESPN (football, NBA, NFL, NHL, rugby, tennis, golf, UFC,
  sport universitaire américain), Jolpica/Ergast (Formule 1), TheSportsDB (rugby, handball, basket,
  volley, hockey, biathlon — avec l'heure de départ des épreuves).

## Lancer localement

```
cd sport-antenne/server
./gradlew run --args="../feed"      # écrit feed/feed.json et feed/feed.json.gz
```

## Production

La tâche planifiée `.github/workflows/feed.yml` lance l'agrégateur toutes les heures et publie le
résultat sur la branche `feed`. L'appli lit :

```
https://raw.githubusercontent.com/aml32/huntstat/feed/feed.json.gz
```

La planification ne démarre qu'une fois le workflow fusionné dans la branche principale du dépôt.
Pour un usage à très grande échelle, servir le flux via GitHub Pages (CDN Fastly) ou un stockage objet.

## Format du flux

`feed.json` : `{ v, updated, source, channels: { id: {n, l} }, events: [ … ] }`.
Chaque événement : `s` sport, `c` compétition, `ci` id de compétition, `co` pays, `h`/`a` équipes,
`st`/`sp` début/fin (ms), `ch` chaînes, `ko` coup d'envoi réel, `vf` direct confirmé, `np` confirmé
au jour seulement, `rp` rediffusion, `sc` score `[dom, ext, état, horloge]`, `rc` épreuve
`[sexe, format, distance, lieu]`, `hl`/`al` logos d'équipes, `de` description.
