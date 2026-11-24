# Pendiente

Anotado el 2026-11-24 para retomar el 2026-11-25.

## Prioridad (para la usuaria)

1. **Imágenes en notas**: subir/pegar como la web (`POST /notes/{id}/images`).
   Hoy una nota con imágenes conserva el Markdown y se abre en modo Markdown.
2. **Server (`servers/notes`): fallos de contrato que afectan a la sincronización**
   - Borrado definitivo sin rastro: `DELETE /notes/permanent`, `DELETE /categories/permanent`
     y la purga de 30 días no dejan tombstone; una nota borrada para siempre en la
     web sigue en el móvil. Propuesta: `GET /notes/deleted?since=` (tabla de tombstones)
     o listado ligero de ids para reconciliar.
   - `PATCH /notes/reorder` no actualiza `updatedAt`; el orden cambiado en la web no
     llega al pull incremental.
   - `CategoryMapper.updateToEntity` ignora `deletedAt`/`nameSlug`: un PATCH restaura
     en silencio una categoría en la papelera.
   - `PATCH /notes/batch` y `/categories/batch` devuelven 404 para todo el lote si falta
     un id; deberían saltarlo como `DELETE` y `restore`.
   - `GET /categories` sin filtro `updatedAt` (se baja entera en cada sync).
   - `updatedAt` es `LocalDateTime` sin zona; mejor `Instant` con offset.

## Funciones de la web que faltan en Android

- Compartir notas con usuarios, enlaces públicos, "compartidas conmigo".
- Reordenar notas arrastrando (el orden manual se ve, no se cambia).
- Vistas configurables (columnas/cuadrícula, view-configs).
- Búsqueda tolerante a errores (fuzzy).
- Contadores en las pestañas de filtro.
- "Actualizado hace…" en el editor.
- Importar/exportar notas y categorías.
- Cambios en tiempo real (socket); hoy se sincroniza al editar, al abrir y a mano.
- Ajustes: cambiar contraseña, privacidad/analítica.
- Registro de cuenta (se crea en la web).
- No aplica en móvil: pestañas de varias notas, atajos de teclado.

## Otros

- APK release firmado para la usuaria (no hay keystore de SitoNotas todavía).
- APK release firmado de SitoWallet 0.1.19 (arreglo de sesión `rememberMe`) para instalar.
- Limitaciones conocidas de sync: cambio de cuenta en el mismo móvil copia notas a la
  nueva cuenta; respuesta perdida tras un batch create duplica notas; el editor no se
  refresca si otro dispositivo cambia la nota abierta.
