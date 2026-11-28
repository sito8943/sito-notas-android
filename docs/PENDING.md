# Pendiente

Actualizado el 2026-11-28 tras la 0.2.0.

## Para publicar la 0.2.0

1. **Desplegar el server** (`sito8943/sito-notes-server#23`) a la vez que la app:
   `PATCH /notes/batch` y `/categories/batch` ahora devuelven `long[]` (la 0.2.0
   entiende los dos formatos; la 0.1.0 no entiende el nuevo). Migraciones V8 y V9.
2. Instalar el APK release firmado de la 0.2.0 (keystore `sitonotas-release.jks`
   fuera de git; hacer copia de seguridad del `.jks` y `keystore.properties`).

## Huecos conocidos

- Pegar una imagen desde el teclado (Gboard) no funciona: haría falta pasar el
  editor a `TextFieldState`. Se cubre con "Pegar imagen" en la barra.
- Los GIF se ven como imagen fija.
- Notas compartidas conmigo: sin imágenes en la vista de solo lectura y pública;
  si el último PATCH falla al salir de una nota con permiso de edición, ese
  cambio se pierde (solo online, como la web).
- El badge de "Compartidas" se refresca al navegar, no por socket.
- Analítica: solo se guarda la preferencia; no se envía nada (habría que dar de
  alta la app Android en sito-feature-usage).
- Registro: depende de `FEATURES_AUTH_REGISTER_ENABLED` en sito-auth (por
  defecto desactivado). No hay pantalla de "revisa tu correo".
- El socket no avisa de creaciones, PATCH en lote, reorden, borrados definitivos
  ni categorías: esos cambios llegan en la siguiente sincronización.
- Si el cursor de tombstones tiene más de 85 días se hace una descarga completa.

## No aplica en móvil

- Pestañas de varias notas, atajos de teclado.
