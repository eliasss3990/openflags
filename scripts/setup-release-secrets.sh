#!/usr/bin/env bash
#
# Configura los secrets de GitHub necesarios para el workflow de release
# de openflags (publicación a Maven Central).
#
# Diseñado para ser:
#   - Idempotente: detecta secrets ya configurados y te pregunta si
#     sobreescribir o saltar.
#   - Re-ejecutable: si falla a la mitad podés volver a correrlo y
#     retomar donde quedaste.
#   - Seguro: nunca imprime valores sensibles. Limpia archivos
#     temporales y variables incluso si lo cancelás con Ctrl+C.
#
# Uso:
#   ./scripts/setup-release-secrets.sh
#

set -euo pipefail

# ---------------------------------------------------------------------------
# Estado global y limpieza
# ---------------------------------------------------------------------------
KEY_FILE=""
PASSPHRASE=""
CENTRAL_USERNAME=""
CENTRAL_PASSWORD=""
SONAR_TOKEN=""

cleanup() {
    if [[ -n "$KEY_FILE" && -f "$KEY_FILE" ]]; then
        shred -u "$KEY_FILE" 2>/dev/null || rm -f "$KEY_FILE"
    fi
    unset PASSPHRASE CENTRAL_USERNAME CENTRAL_PASSWORD SONAR_TOKEN
}
trap cleanup EXIT INT TERM

# ---------------------------------------------------------------------------
# Helpers de salida
# ---------------------------------------------------------------------------
bold() { printf '\n\033[1m%s\033[0m\n' "$*"; }
info() { printf '  %s\n' "$*"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$*"; }
err()  { printf '  \033[31m✗\033[0m %s\n' "$*" >&2; }

confirm() {
    local prompt="$1" default="${2:-Y}" ans
    if [[ "$default" =~ ^[Yy]$ ]]; then
        read -rp "  $prompt [Y/n] " ans
        [[ -z "$ans" || "$ans" =~ ^[Yy]$ ]]
    else
        read -rp "  $prompt [y/N] " ans
        [[ "$ans" =~ ^[Yy]$ ]]
    fi
}

read_nonempty() {
    local prompt="$1" hidden="${2:-no}" var="" attempts=0
    while [[ -z "$var" ]]; do
        if [[ "$hidden" == "yes" ]]; then
            read -rsp "  $prompt: " var
            echo
        else
            read -rp "  $prompt: " var
        fi
        if [[ -z "$var" ]]; then
            warn "Valor vacío. Volvé a intentar (Ctrl+C para cancelar)."
            ((++attempts))
            if (( attempts >= 3 )); then
                err "Demasiados intentos vacíos. Abortando."
                exit 1
            fi
        fi
    done
    printf '%s' "$var"
}

# ---------------------------------------------------------------------------
# Pre-checks
# ---------------------------------------------------------------------------
require_cmd() {
    command -v "$1" >/dev/null 2>&1 || { err "Falta el comando: $1"; exit 1; }
}

bold "Pre-checks"
for cmd in gpg gh git; do require_cmd "$cmd"; done
if ! command -v shred >/dev/null 2>&1; then
    warn "shred no disponible — usaré rm normal para borrar el archivo temporal."
fi
ok "Comandos requeridos presentes."

if ! git rev-parse --show-toplevel >/dev/null 2>&1; then
    err "No estás dentro de un repositorio git."
    exit 1
fi
cd "$(git rev-parse --show-toplevel)"
ok "Repo: $(pwd)"

if ! gh auth status >/dev/null 2>&1; then
    err "gh no está autenticado. Corré: gh auth login"
    exit 1
fi
REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || true)"
if [[ -z "$REPO" ]]; then
    err "No pude detectar el repo destino. ¿Estás en el repo correcto y con permisos?"
    exit 1
fi
ok "Repositorio destino: $REPO"

# ---------------------------------------------------------------------------
# Cache: qué secrets ya existen
# ---------------------------------------------------------------------------
mapfile -t EXISTING_SECRETS < <(gh secret list --json name -q '.[].name' 2>/dev/null || true)
secret_exists() {
    local needle="$1" s
    for s in "${EXISTING_SECRETS[@]:-}"; do
        [[ "$s" == "$needle" ]] && return 0
    done
    return 1
}

should_set() {
    local name="$1"
    if secret_exists "$name"; then
        if confirm "$name ya existe. ¿Sobreescribir?" N; then
            return 0
        fi
        info "Saltando $name."
        return 1
    fi
    return 0
}

set_secret() {
    local name="$1" value="$2"
    if [[ -z "$value" ]]; then
        err "Valor vacío para $name; aborto sin tocar el secret."
        return 1
    fi
    printf '%s' "$value" | gh secret set "$name" --body -
    ok "$name configurado."
}

# ---------------------------------------------------------------------------
# 1. Detectar / elegir clave GPG
# ---------------------------------------------------------------------------
bold "Paso 1/6 — Clave GPG"

mapfile -t GPG_KEYS < <(gpg --list-secret-keys --with-colons 2>/dev/null \
    | awk -F: '/^sec:/ {print $5}')

if [[ ${#GPG_KEYS[@]} -eq 0 ]]; then
    err "No tenés ninguna clave GPG privada. Generá una con:"
    err "  gpg --full-generate-key  (rsa4096, sin expiración o > 2 años)"
    exit 1
fi

if [[ ${#GPG_KEYS[@]} -eq 1 ]]; then
    GPG_KEY="${GPG_KEYS[0]}"
    ok "Única clave detectada: $GPG_KEY"
else
    info "Tenés varias claves. Elegí la que va a firmar los releases:"
    select GPG_KEY in "${GPG_KEYS[@]}"; do
        [[ -n "$GPG_KEY" ]] && break
    done
fi

# ---------------------------------------------------------------------------
# 2. Publicar pública en keyserver
# ---------------------------------------------------------------------------
bold "Paso 2/6 — Publicar pública en keys.openpgp.org"
info "Maven Central verifica firmas contra el keyserver. Es idempotente."
if confirm "¿Publicar / republicar ahora?" Y; then
    if gpg --keyserver keys.openpgp.org --send-keys "$GPG_KEY"; then
        ok "Publicada."
    else
        warn "Falló el envío al keyserver. Podés reintentar después con:"
        warn "  gpg --keyserver keys.openpgp.org --send-keys $GPG_KEY"
    fi
else
    info "Saltado. Hacelo antes del primer tag de release."
fi

# ---------------------------------------------------------------------------
# 3. MAVEN_GPG_PRIVATE_KEY
# ---------------------------------------------------------------------------
bold "Paso 3/6 — MAVEN_GPG_PRIVATE_KEY"
if should_set MAVEN_GPG_PRIVATE_KEY; then
    KEY_FILE="$(mktemp -t openflags-key.XXXXXX.asc)"
    chmod 600 "$KEY_FILE"
    export GPG_TTY="${GPG_TTY:-$(tty 2>/dev/null || echo /dev/tty)}"

    info "Te va a pedir la passphrase de tu clave GPG..."
    if ! gpg --pinentry-mode loopback --armor --export-secret-keys "$GPG_KEY" > "$KEY_FILE"; then
        err "Falló la exportación."
        exit 1
    fi

    if ! head -1 "$KEY_FILE" | grep -q "BEGIN PGP PRIVATE KEY BLOCK"; then
        err "Lo exportado no parece una clave privada válida."
        exit 1
    fi

    if ! [[ -s "$KEY_FILE" ]]; then
        err "El archivo exportado está vacío."
        exit 1
    fi

    gh secret set MAVEN_GPG_PRIVATE_KEY < "$KEY_FILE"
    ok "MAVEN_GPG_PRIVATE_KEY configurado."

    if command -v shred >/dev/null 2>&1; then
        shred -u "$KEY_FILE"
    else
        rm -f "$KEY_FILE"
    fi
    KEY_FILE=""
fi

# ---------------------------------------------------------------------------
# 4. MAVEN_GPG_PASSPHRASE
# ---------------------------------------------------------------------------
bold "Paso 4/6 — MAVEN_GPG_PASSPHRASE"
if should_set MAVEN_GPG_PASSPHRASE; then
    info "Misma passphrase que usaste recién. Se ingresa oculta."
    while :; do
        PASSPHRASE="$(read_nonempty "Passphrase" yes)"
        PASSPHRASE_CONFIRM="$(read_nonempty "Confirmá passphrase" yes)"
        if [[ "$PASSPHRASE" == "$PASSPHRASE_CONFIRM" ]]; then
            unset PASSPHRASE_CONFIRM
            break
        fi
        unset PASSPHRASE_CONFIRM
        warn "No coinciden. Reintentá."
    done
    set_secret MAVEN_GPG_PASSPHRASE "$PASSPHRASE"
    PASSPHRASE=""
fi

# ---------------------------------------------------------------------------
# 5. Sonatype Central
# ---------------------------------------------------------------------------
bold "Paso 5/6 — CENTRAL_USERNAME / CENTRAL_PASSWORD"
info "Generá un User Token en https://central.sonatype.com"
info "  → Account → Generate User Token"
info "Tiene 2 partes (un username y un password opacos). Las dos son secrets."

if should_set CENTRAL_USERNAME; then
    CENTRAL_USERNAME="$(read_nonempty 'CENTRAL_USERNAME' no)"
    set_secret CENTRAL_USERNAME "$CENTRAL_USERNAME"
    CENTRAL_USERNAME=""
fi

if should_set CENTRAL_PASSWORD; then
    CENTRAL_PASSWORD="$(read_nonempty 'CENTRAL_PASSWORD (oculto)' yes)"
    set_secret CENTRAL_PASSWORD "$CENTRAL_PASSWORD"
    CENTRAL_PASSWORD=""
fi

# ---------------------------------------------------------------------------
# 6. SonarCloud
# ---------------------------------------------------------------------------
bold "Paso 6/6 — SONAR_TOKEN"
info "Generalo en https://sonarcloud.io"
info "  → My Account → Security → Generate Token"

if should_set SONAR_TOKEN; then
    SONAR_TOKEN="$(read_nonempty 'SONAR_TOKEN (oculto)' yes)"
    set_secret SONAR_TOKEN "$SONAR_TOKEN"
    SONAR_TOKEN=""
fi

# ---------------------------------------------------------------------------
# Verificación final
# ---------------------------------------------------------------------------
bold "Verificación final"
gh secret list

REQUIRED=(MAVEN_GPG_PRIVATE_KEY MAVEN_GPG_PASSPHRASE CENTRAL_USERNAME CENTRAL_PASSWORD SONAR_TOKEN)
mapfile -t FINAL_SECRETS < <(gh secret list --json name -q '.[].name')
MISSING=()
for r in "${REQUIRED[@]}"; do
    found=0
    for s in "${FINAL_SECRETS[@]}"; do
        [[ "$s" == "$r" ]] && { found=1; break; }
    done
    (( found )) || MISSING+=("$r")
done

echo
if (( ${#MISSING[@]} == 0 )); then
    ok "Los 5 secrets requeridos están configurados."
    info "Próximo paso: mergear release/prep-1.0.0 y cortar el tag v1.0.0."
else
    warn "Faltan secrets: ${MISSING[*]}"
    warn "Volvé a correr el script para completarlos."
    exit 1
fi
