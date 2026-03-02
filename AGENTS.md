# AGENTS.md

## Cursor Cloud specific instructions

### Services overview

| Service | Port | Description |
|---------|------|-------------|
| Backend (Spring Boot) | 6060 | Java REST API at `/api` context path |
| Frontend (Vite) | 3030 | Vue 3 SPA, proxies `/api` to backend |
| MySQL | 3306 | Required database |

### Starting services

**MySQL** must be running before the backend. Start it with:
```
sudo mysqld_safe --user=mysql &
```

**Backend** (from repo root):
```
cd backend && mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Djava.awt.headless=true" \
  -Dspring-boot.run.arguments="--spring.redis.host=disabled"
```

**Frontend** (from repo root):
```
cd frontend && npm run dev
```

### Gotchas

- **Redis config misplacement**: In `application.yml`, the `redis:` block is nested under `logging:` instead of `spring:`. This means `spring.redis.host` is never set. You must pass `--spring.redis.host=disabled` as a Spring Boot argument so the `ConcurrentMapCacheManager` fallback bean is activated. Without this, the app fails to start with a missing `CacheManager` bean error.
- **ESLint not installed**: The `npm run lint` script references `eslint` but it is not in `devDependencies`. This command will fail. Use `npx vite build` to validate frontend code instead.
- **vue-tsc incompatible with Node 22**: The pinned `vue-tsc@1.8.27` throws on Node 22+. Type-checking via `vue-tsc --noEmit` is not available in this environment.
- **ONNX models not in repo**: AI features (face detection, scoring, background removal) require `.onnx` model files in `./models/`. These are `.gitignore`d and not present. The app starts fine without them but logs warnings.
- **Default admin credentials**: username `admin`, password `admin`. The README mentions `admin123` but the actual default is `admin`.
- **MySQL setup**: The `application.yml` configures MySQL with `root` / `iwxyiroot`. In cloud dev, MySQL is installed via apt and the root password is set to `iwxyiroot` with database `photo_exhibition`.
- **Backend tests**: Run with `cd backend && mvn test`. Tests are minimal (1 test for color analysis) and pass.
- **Frontend build**: Run with `cd frontend && npx vite build`.
