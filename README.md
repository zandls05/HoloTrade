
## GitHub Actions
`.github/workflows/build.yml` compila con JDK 25 en cada push/PR y sube el jar como artefacto.
Para publicar un release con el jar adjunto: `git tag v2.0.0 && git push origin v2.0.0`.
