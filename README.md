# Photo App Backend

Backend d'une application de galerie photo privée à 2 utilisateurs. Sécurité et
confidentialité sont les priorités : authentification JWT stateless, chiffrement
AES-256-GCM des fichiers avant écriture en GridFS, verrouillage de compte après
tentatives échouées, et aucune inscription publique.

## Stack technique

- Java 21, Spring Boot 3.3
- Spring Security (JWT stateless)
- Spring Data MongoDB + GridFS
- Chiffrement AES-256-GCM (IV aléatoire par fichier)
- BCrypt (coût 12) pour les mots de passe
- Maven, Docker (multi-stage build)

## Choix d'implémentation notables

- **Pas de refresh token** : le JWT expire après 24h (configurable), l'utilisateur
  doit se reconnecter à l'expiration. Choix volontaire pour réduire la surface
  d'attaque sur une application à 2 utilisateurs.
- **Clé de chiffrement unique et statique** : `ENCRYPTION_KEY` est utilisée pour
  chiffrer/déchiffrer tous les fichiers. Il n'y a pas de mécanisme de rotation ;
  si la clé doit changer, il faudra déchiffrer manuellement l'historique avec
  l'ancienne clé et le réchiffrer avec la nouvelle.
- **Rate limiting in-memory** : limite simple par IP (`ConcurrentHashMap` +
  fenêtre glissante), sans dépendance externe. Non distribué (valable pour une
  seule instance backend).
- **Verrouillage de compte** : après 5 échecs consécutifs, le compte est verrouillé
  15 minutes. Le compteur d'échecs est remis à zéro automatiquement dès que le
  verrou expire.

## Prérequis

- Java 21+
- Maven 3.9+ (ou utiliser le Dockerfile qui embarque Maven)
- Docker + Docker Compose (pour le déploiement local complet)
- Une instance MongoDB (fournie par `docker-compose.yml` en local)

## Variables d'environnement

Toutes les valeurs sensibles sont externalisées. Aucune valeur par défaut de
production n'est fournie pour les secrets — les valeurs par défaut du fichier
`application.yml` sont explicitement marquées `CHANGE_ME_IN_PRODUCTION` et ne
doivent jamais être utilisées telles quelles.

| Variable | Description | Exemple |
|---|---|---|
| `MONGO_URI` | URI de connexion MongoDB | `mongodb://localhost:27017/photoapp` |
| `JWT_SECRET` | Secret de signature JWT (≥ 256 bits) | générer avec `openssl rand -base64 48` |
| `JWT_EXPIRATION_MS` | Durée de validité du JWT (ms) | `86400000` (24h) |
| `ENCRYPTION_KEY` | Clé AES-256 (exactement 32 octets UTF-8) | `openssl rand -hex 16` (32 caractères hex) |
| `FRONTEND_URL` | Origine autorisée pour CORS | `http://localhost:4200` |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | Identifiants du 1er compte, créés au démarrage | — |
| `PARTNER_USERNAME` / `PARTNER_PASSWORD` | Identifiants du 2e compte, créés au démarrage | — |
| `MAX_UPLOAD_SIZE_MB` | Taille max par fichier uploadé | `20` |

> `ENCRYPTION_KEY` doit faire exactement 32 caractères (32 octets en UTF-8) :
> l'application refuse de démarrer sinon. `JWT_SECRET` doit faire au moins 32
> caractères (256 bits).

## Lancer en local avec Docker Compose

1. Créer un fichier `.env` à la racine (non commité) avec au minimum :

```env
JWT_SECRET=change-me-to-a-random-256-bit-secret-xxxxxxxxxx
ENCRYPTION_KEY=32-characters-exactly-here-1234
FRONTEND_URL=http://localhost:4200
ADMIN_USERNAME=admin
ADMIN_PASSWORD=change-me
PARTNER_USERNAME=partner
PARTNER_PASSWORD=change-me-too
```

2. Démarrer les services :

```bash
docker compose up --build
```

Le backend est disponible sur `http://localhost:8080`, MongoDB persiste ses
données dans le volume Docker `mongo-data`.

## Lancer en local sans Docker

```bash
# Démarrer une instance MongoDB locale (ou via docker run mongo:7.0)
export MONGO_URI=mongodb://localhost:27017/photoapp
export JWT_SECRET=...
export ENCRYPTION_KEY=...
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=...
export PARTNER_USERNAME=partner
export PARTNER_PASSWORD=...

mvn spring-boot:run
```

## Endpoints

Toutes les réponses d'erreur suivent le format uniforme :
```json
{ "data": null, "error": "message", "timestamp": "2026-08-11T21:00:00Z" }
```

### Authentification

**POST /api/auth/login** — public, limité à 10 tentatives/minute par IP.

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "change-me"}'
```

Réponse :
```json
{ "token": "eyJ...", "expiresAt": 1735689600000, "username": "admin" }
```

### Photos (toutes protégées par JWT — header `Authorization: Bearer <token>`)

**POST /api/photos/upload** — upload multi-fichiers (JPEG/PNG/WEBP/HEIC, 20 Mo max/fichier)

```bash
curl -X POST http://localhost:8080/api/photos/upload \
  -H "Authorization: Bearer <token>" \
  -F "files=@photo1.jpg" \
  -F "files=@photo2.jpg"
```

**GET /api/photos?page=0&size=30** — liste paginée triée par date de prise de vue

```bash
curl http://localhost:8080/api/photos?page=0&size=30 \
  -H "Authorization: Bearer <token>"
```

**GET /api/photos/{id}** — métadonnées d'une photo

```bash
curl http://localhost:8080/api/photos/<id> \
  -H "Authorization: Bearer <token>"
```

**GET /api/photos/{id}/content** — fichier original déchiffré (streaming)

```bash
curl http://localhost:8080/api/photos/<id>/content \
  -H "Authorization: Bearer <token>" -o photo.jpg
```

**GET /api/photos/{id}/thumbnail** — miniature déchiffrée (streaming)

```bash
curl http://localhost:8080/api/photos/<id>/thumbnail \
  -H "Authorization: Bearer <token>" -o thumb.jpg
```

**DELETE /api/photos/{id}** — supprime la photo (métadonnées + fichiers GridFS)

```bash
curl -X DELETE http://localhost:8080/api/photos/<id> \
  -H "Authorization: Bearer <token>"
```

**PATCH /api/photos/{id}/favorite** — bascule le statut favori

```bash
curl -X PATCH http://localhost:8080/api/photos/<id>/favorite \
  -H "Authorization: Bearer <token>"
```

## Tests

```bash
# Tests unitaires (AuthService, EncryptionService, PhotoService)
mvn test

# Test d'intégration Testcontainers (nécessite Docker démarré)
mvn test -Dtest=*IntegrationTest -DfailIfNoTests=false
```

Le test d'intégration `PhotoUploadIntegrationTest` démarre un conteneur MongoDB
réel et exerce le flux complet d'upload : chiffrement, stockage GridFS,
détection de doublon, persistance des métadonnées.

## Déploiement

### Build du jar

```bash
mvn clean package -DskipTests
# Génère target/photo-app-backend-1.0.0.jar
```

### Build de l'image Docker

```bash
docker build -t photoapp-backend:latest .
```

### Lancer le conteneur

```bash
docker run -d -p 8080:8080 \
  -e MONGO_URI=mongodb://<host>:27017/photoapp \
  -e JWT_SECRET=<secret> \
  -e ENCRYPTION_KEY=<32-byte-key> \
  -e FRONTEND_URL=https://your-frontend.example \
  -e ADMIN_USERNAME=admin -e ADMIN_PASSWORD=<pwd> \
  -e PARTNER_USERNAME=partner -e PARTNER_PASSWORD=<pwd> \
  photoapp-backend:latest
```

L'image finale tourne en tant qu'utilisateur non-root sur une base
`eclipse-temurin:21-jre-alpine` (image légère, sans outils de build).
