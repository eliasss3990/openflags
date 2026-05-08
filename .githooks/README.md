# githooks

Hooks de Git versionados con el repo. Se activan **opt-in por desarrollador** la primera vez:

```bash
./.githooks/install.sh
```

Esto setea `core.hooksPath=.githooks` en tu clone (no toca el repo remoto).

## Hooks instalados

### `commit-msg`

Obliga a actualizar `CHANGELOG.md` en cada commit sustantivo.

**Se saltea automaticamente cuando:**

- es un merge / revert / fixup / squash
- los archivos staged son solo docs (`*.md`, `docs/`, `LICENSE`), CI (`.github/`), meta (`.gitignore`, `.editorconfig`) o los propios hooks (`.githooks/`)

**Bypass manual:** agregar `[skip-changelog]` (o `[no-changelog]`) en cualquier parte del mensaje del commit.

```text
fix(core): renombrar variable interna [skip-changelog]
```

Solo usar el bypass cuando realmente no hay cambio de comportamiento que registrar (typos, formato, comentarios). Si el cambio afecta el comportamiento publico, **anhadir entrada al CHANGELOG**.

### Limitaciones conocidas

- Eliminar archivos sustantivos (`git rm src/Foo.java`) **si** dispara la verificacion (deletions estan incluidas en el diff-filter). Si la eliminacion no merece nota, usar `[skip-changelog]`.
- Paths con caracteres no-ASCII se procesan tal como los reporta `git diff --cached --name-only` (sin la opcion `-z`); el match es exacto sobre la cadena cruda. En la practica el unico path al que se le pide match exacto es `CHANGELOG.md`, que vive en la raiz, asi que no afecta el flujo normal.
