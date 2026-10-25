# Moorty Voice para Android

Proyecto fuente Android para TikTok LIVE y YouTube LIVE. La interfaz se simplificó para dejar las conexiones principales visibles y guardar los ajustes avanzados dentro de **Configuración y sonidos**.

## Corrección de TikTok

El proxy de callbacks devuelve ahora valores por defecto compatibles con el tipo de retorno (incluidos `int`, `boolean`, `long`, etc.) y trata `hashCode`, `equals` y `toString` correctamente. Esto aborda una causa típica del error:

`Expected to unbox "int" primitive type but was returned null`

No se puede garantizar que el conector comunitario funcione en todos los directos: TikTok cambia su API no oficial y algunos eventos/campos pueden no llegar. La compilación del APK y la prueba de conexión real deben confirmarse en GitHub Actions y en el teléfono.

## Subir el proyecto a GitHub desde cero

1. Descarga y descomprime este ZIP.
2. En GitHub, crea un repositorio vacío o abre el repositorio que quieres reemplazar.
3. Sube **el contenido de esta carpeta** (no una carpeta adicional que lo envuelva). En la raíz deben verse `app`, `.github`, `build.gradle.kts`, `settings.gradle.kts` y `gradle.properties`.
4. Si usas la web de GitHub desde el teléfono, entra en **Add file → Upload files**, selecciona los archivos y carpetas disponibles, y confirma el commit en la rama `main`.
5. Abre **Actions → Build Voice APK**. Al terminar en verde, abre la ejecución y descarga el artefacto `Voice-Android-debug-APK`.

## Configuración en la app

- TikTok: escribe el usuario sin necesidad de `@` y toca **Conectar TikTok**.
- Sonidos: abre **Configuración y sonidos** y toca cada opción de sonido para elegir un archivo de audio del teléfono.
- YouTube: dentro de la misma configuración introduce la API key de YouTube Data API v3 y el ID del canal, guarda y luego toca **Conectar YouTube**.

## Límites conocidos

- TikTokLiveJava es una librería comunitaria/no oficial; eventos como entradas y estado de suscriptor dependen de los datos que entregue el conector.
- La API key de YouTube permite leer chat público y contador público de espectadores cuando el directo/API lo permiten. No permite por sí sola consultar la lista de nuevos suscriptores recientes.
- Este ZIP contiene código fuente, no un APK ya probado. El workflow debe compilarlo y la conexión debe probarse en un directo real.
