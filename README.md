# Reencuadrador v5

Aplicación Android ligera para reencuadrar fotografías directamente en el móvil.

El repositorio incluye también [`Rencuadrar.py`](./Rencuadrar.py), el programa original de escritorio en Python/Tkinter que sirvió como referencia funcional para la versión Android.

## APK

La versión compilada incluida en el repositorio es:

[`Reencuadrador-v5.apk`](./Reencuadrador-v5.apk)

## Funciones

- Selección de carpeta mediante Storage Access Framework.
- Lectura recursiva de fotos en carpetas y subcarpetas.
- Rectángulo de recorte ajustable y desplazable con guía de tercios.
- Botones `Carpeta`, `Reset`, `Saltar` y `Aceptar`.
- Guarda la copia en la carpeta original con sufijo `_ENC`.
- Mantiene un registro por carpeta para continuar otro día.
- Respeta la orientación EXIF antes de mostrar y recortar.
- Corrige rotaciones de 90, 180 y 270 grados y orientaciones espejadas.
- Funciona completamente en local.

## Identidad Android

- Paquete: `com.ricardo.reencuadrador.v5`
- Version code: `5`
- Version name: `5.0`
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
build/Reencuadrador-v5.apk
```

El APK publicado es una compilación de depuración firmada para instalación manual.
