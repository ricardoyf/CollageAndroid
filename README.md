<!-- app-release:start -->
[**Descargar APK v1.5**](https://github.com/ricardoyf/CollageAndroid/releases/download/v1.5/Collage-v1.5.apk) · [SHA-256](https://github.com/ricardoyf/CollageAndroid/releases/download/v1.5/Collage-v1.5.apk.sha256)

`7168b9a5803c634a76f526d1b73620e1bbaebdc5187f6f771441c8e07b508beb`
<!-- app-release:end -->

# Collage Android v1.5

Aplicación Android ligera para recortar fotografías en móvil con proporciones de impresión.

Parte de Reencuadrador v6.5 y adapta al móvil los presets de proporción del script `collages_v3.py`.

## APK

La versión compilada incluida en el repositorio es:

[`collage.apk`](./collage.apk)

## Funciones

- Selección de carpeta mediante Storage Access Framework.
- Lectura recursiva de fotos en carpetas y subcarpetas.
- Rectángulo de recorte ajustable y desplazable con guía de tercios.
- Presets de proporción `10x15`, `13x18`, `15x20`, `18x24`, `20x30` y `Libre`.
- Botón `H/V` para alternar horizontal/vertical.
- Botones `Carpeta`, `Fotos`, `Reciente`, `Saltar`, `Reset`, `Aceptar` y `Compartir`.
- Botón `Fotos` para elegir una foto pendiente concreta viendo miniaturas.
- Botón `Reciente` para saltar a la foto pendiente más reciente.
- Al abrir o refrescar carpeta, empieza directamente por la foto pendiente más reciente.
- Escanea la carpeta en segundo plano para que el arranque no se quede colgado en Camera.
- Evita consultas repetidas por cada foto al detectar copias `COPIAde` o `ENC`.
- Limita la carga de imagen en memoria para reducir bloqueos con fotos grandes.
- Rotación fina con `-1°` y `+1°` para enderezar antes de aceptar.
- Guarda el reencuadre con sufijo `ENC` y proporción, por ejemplo `_ENC_10x15H` o `_ENC_20x30V`.
- Los reencuadres `ENC` se guardan en JPEG 95, RGB de 8 bits, con metadatos de impresion a 300 ppp y etiqueta sRGB.
- El tamano objetivo se calcula a 300 ppp para cada formato. Ejemplo: 13x18 cm usa 1535x2126 px en vertical y 2126x1535 px en horizontal.
- Si el recorte supera el tamano objetivo se reduce; si no llega, se mantiene sin ampliar.
- Interruptor `Malo` para crear marcador `_MALO.txt` y saltar fotos descartadas.
- Boton `Compartir` para guardar el reencuadre actual, abrir el dialogo de compartir y avanzar a la siguiente foto.
- Interruptor `COPIAde` para crear una copia marcada como selección/favorita.
- Si `COPIAde` está activo y se acepta el reencuadre, la salida combina ambos sufijos, por ejemplo `_COPIAde_ENC_10x15H`.
- El selector de fotos muestra un check en miniaturas con marcas `COPIAde` o `ENC`.
- Selector `Marcadas` para ver solo fotos que ya tienen copia `COPIAde` o reencuadre `ENC`; mantener pulsada una fila abre la foto en pantalla completa.
- Mantiene un registro por carpeta para continuar otro día.
- Respeta la orientación EXIF antes de mostrar y recortar.
- Corrige rotaciones de 90, 180 y 270 grados y orientaciones espejadas.
- Funciona completamente en local.
- Version visible en pantalla.

## Version 1.5

- Al escanear, Collage ya no muestra fotos originales que tengan una marca `ENC` o `COPIAde` en la misma carpeta.

## Identidad Android

- Paquete: `com.ricardo.collage`
- Version code: `6`
- Version name: `1.5`
- Android mínimo: API 24
- Target: API 34

## Compilar

Requiere JDK, Android SDK, plataforma Android 34 o posterior y Build Tools 35.0.0.

```bash
export ANDROID_SDK_ROOT=/ruta/al/Android/Sdk
./build.sh
```

El APK se genera en:

```text
build/collage.apk
```

El APK publicado es una compilación de depuración firmada para instalación manual.
