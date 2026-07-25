# -*- coding: utf-8 -*-
"""
Reencuadrador simple de fotos

Uso:
1. Guarda este script dentro de la carpeta donde están las fotos.
2. Ejecútalo.
3. Mueve las esquinas del rectángulo.
4. Pulsa Aceptar.
5. Se guarda una copia con sufijo _ENC y pasa a la siguiente foto.

Controles:
- Arrastrar esquinas: ajustar reencuadre
- Arrastrar dentro del rectángulo: mover reencuadre entero
- Aceptar / Enter: guardar y pasar a la siguiente
- Saltar: dejar esta foto para más adelante
- Reset: volver al encuadre inicial
- Escape: salir
"""

import os
import json
import tkinter as tk
from tkinter import messagebox
from pathlib import Path
from PIL import Image, ImageTk, ImageOps


# ================= CONFIGURACIÓN =================

SUPPORTED_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".tif", ".tiff"}

SUFIJO_SALIDA = "_ENC"
ESTADO_JSON = "reencuadre_estado.json"

MAX_PREVIEW_W = 1400
MAX_PREVIEW_H = 850

HANDLE_SIZE = 12
MIN_CROP_SIZE = 40


# ================= UTILIDADES =================

def cargar_estado():
    if os.path.exists(ESTADO_JSON):
        try:
            with open(ESTADO_JSON, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {"procesadas": []}
    return {"procesadas": []}


def guardar_estado(estado):
    with open(ESTADO_JSON, "w", encoding="utf-8") as f:
        json.dump(estado, f, indent=2, ensure_ascii=False)


def es_foto_valida(path: Path):
    if path.suffix.lower() not in SUPPORTED_EXTS:
        return False

    if path.stem.endswith(SUFIJO_SALIDA):
        return False

    return True


def nombre_salida(path: Path):
    return path.with_name(f"{path.stem}{SUFIJO_SALIDA}{path.suffix}")


def buscar_fotos():
    carpeta = Path.cwd()

    fotos = []
    for item in sorted(carpeta.iterdir()):
        if not item.is_file():
            continue

        if not es_foto_valida(item):
            continue

        fotos.append(item)

    return fotos


# ================= APLICACIÓN =================

class Reencuadrador:
    def __init__(self, root):
        self.root = root
        self.root.title("Reencuadrador de fotos")

        self.estado = cargar_estado()
        self.procesadas = set(self.estado.get("procesadas", []))

        todas = buscar_fotos()

        self.fotos = []
        for foto in todas:
            salida = nombre_salida(foto)

            if foto.name in self.procesadas:
                continue

            if salida.exists():
                self.procesadas.add(foto.name)
                continue

            self.fotos.append(foto)

        self.indice = 0

        self.img_original = None
        self.img_preview = None
        self.tk_img = None

        self.scale = 1.0
        self.offset_x = 0
        self.offset_y = 0

        self.crop = None
        self.drag_mode = None
        self.last_mouse = None

        self.crear_interfaz()

        self.root.bind("<Return>", lambda e: self.aceptar())
        self.root.bind("<Escape>", lambda e: self.salir())

        self.cargar_siguiente()

    def crear_interfaz(self):
        self.frame_top = tk.Frame(self.root)
        self.frame_top.pack(fill="x", padx=10, pady=8)

        self.lbl_info = tk.Label(
            self.frame_top,
            text="",
            anchor="w",
            font=("Arial", 11)
        )
        self.lbl_info.pack(side="left", fill="x", expand=True)

        self.btn_reset = tk.Button(
            self.frame_top,
            text="Reset",
            command=self.reset_crop,
            width=12
        )
        self.btn_reset.pack(side="right", padx=4)

        self.btn_saltar = tk.Button(
            self.frame_top,
            text="Saltar",
            command=self.saltar,
            width=12
        )
        self.btn_saltar.pack(side="right", padx=4)

        self.btn_aceptar = tk.Button(
            self.frame_top,
            text="Aceptar",
            command=self.aceptar,
            width=12
        )
        self.btn_aceptar.pack(side="right", padx=4)

        self.canvas = tk.Canvas(
            self.root,
            bg="#222222",
            cursor="crosshair"
        )
        self.canvas.pack(fill="both", expand=True, padx=10, pady=10)

        self.canvas.bind("<ButtonPress-1>", self.mouse_down)
        self.canvas.bind("<B1-Motion>", self.mouse_drag)
        self.canvas.bind("<ButtonRelease-1>", self.mouse_up)

    def cargar_siguiente(self):
        if self.indice >= len(self.fotos):
            guardar_estado({
                "procesadas": sorted(self.procesadas)
            })
            messagebox.showinfo(
                "Terminado",
                "No quedan fotos pendientes de reencuadrar."
            )
            self.root.destroy()
            return

        self.path_actual = self.fotos[self.indice]

        try:
            img = Image.open(self.path_actual)
            img = ImageOps.exif_transpose(img)
            self.img_original = img
        except Exception as e:
            messagebox.showwarning(
                "Error",
                f"No se pudo abrir:\n{self.path_actual.name}\n\n{e}"
            )
            self.indice += 1
            self.cargar_siguiente()
            return

        self.preparar_preview()
        self.reset_crop()

        pendientes = len(self.fotos) - self.indice
        total = len(self.fotos)

        self.lbl_info.config(
            text=f"{self.indice + 1}/{total} - {self.path_actual.name} | Pendientes: {pendientes}"
        )

    def preparar_preview(self):
        img = self.img_original
        w, h = img.size

        scale_w = MAX_PREVIEW_W / w
        scale_h = MAX_PREVIEW_H / h
        self.scale = min(scale_w, scale_h, 1.0)

        preview_w = int(w * self.scale)
        preview_h = int(h * self.scale)

        self.img_preview = img.resize(
            (preview_w, preview_h),
            Image.Resampling.LANCZOS
        )

        self.tk_img = ImageTk.PhotoImage(self.img_preview)

        self.canvas.config(width=preview_w, height=preview_h)
        self.canvas.delete("all")

        self.offset_x = 0
        self.offset_y = 0

        self.canvas.create_image(
            self.offset_x,
            self.offset_y,
            anchor="nw",
            image=self.tk_img,
            tags="foto"
        )

    def reset_crop(self):
        w, h = self.img_preview.size

        margen_x = int(w * 0.08)
        margen_y = int(h * 0.08)

        self.crop = [
            margen_x,
            margen_y,
            w - margen_x,
            h - margen_y
        ]

        self.dibujar_crop()

    def dibujar_crop(self):
        self.canvas.delete("crop")
        self.canvas.delete("handle")
        self.canvas.delete("overlay")

        x1, y1, x2, y2 = self.crop
        w, h = self.img_preview.size

        # Zonas oscuras exteriores
        self.canvas.create_rectangle(0, 0, w, y1, fill="black", stipple="gray50", outline="", tags="overlay")
        self.canvas.create_rectangle(0, y2, w, h, fill="black", stipple="gray50", outline="", tags="overlay")
        self.canvas.create_rectangle(0, y1, x1, y2, fill="black", stipple="gray50", outline="", tags="overlay")
        self.canvas.create_rectangle(x2, y1, w, y2, fill="black", stipple="gray50", outline="", tags="overlay")

        # Rectángulo de recorte
        self.canvas.create_rectangle(
            x1, y1, x2, y2,
            outline="yellow",
            width=2,
            tags="crop"
        )

        # Líneas de tercios
        tercio_x = (x2 - x1) / 3
        tercio_y = (y2 - y1) / 3

        for i in [1, 2]:
            xx = x1 + tercio_x * i
            yy = y1 + tercio_y * i

            self.canvas.create_line(
                xx, y1, xx, y2,
                fill="yellow",
                dash=(4, 4),
                tags="crop"
            )
            self.canvas.create_line(
                x1, yy, x2, yy,
                fill="yellow",
                dash=(4, 4),
                tags="crop"
            )

        # Esquinas
        for nombre, x, y in self.get_handles():
            self.canvas.create_rectangle(
                x - HANDLE_SIZE // 2,
                y - HANDLE_SIZE // 2,
                x + HANDLE_SIZE // 2,
                y + HANDLE_SIZE // 2,
                fill="yellow",
                outline="black",
                tags=("handle", nombre)
            )

    def get_handles(self):
        x1, y1, x2, y2 = self.crop

        return [
            ("tl", x1, y1),
            ("tr", x2, y1),
            ("bl", x1, y2),
            ("br", x2, y2),
        ]

    def detectar_zona(self, x, y):
        for nombre, hx, hy in self.get_handles():
            if abs(x - hx) <= HANDLE_SIZE and abs(y - hy) <= HANDLE_SIZE:
                return nombre

        x1, y1, x2, y2 = self.crop

        if x1 <= x <= x2 and y1 <= y <= y2:
            return "move"

        return None

    def mouse_down(self, event):
        self.drag_mode = self.detectar_zona(event.x, event.y)
        self.last_mouse = (event.x, event.y)

    def mouse_drag(self, event):
        if not self.drag_mode:
            return

        x, y = event.x, event.y
        last_x, last_y = self.last_mouse
        dx = x - last_x
        dy = y - last_y

        img_w, img_h = self.img_preview.size
        x1, y1, x2, y2 = self.crop

        if self.drag_mode == "move":
            nuevo_x1 = x1 + dx
            nuevo_y1 = y1 + dy
            nuevo_x2 = x2 + dx
            nuevo_y2 = y2 + dy

            ancho = x2 - x1
            alto = y2 - y1

            if nuevo_x1 < 0:
                nuevo_x1 = 0
                nuevo_x2 = ancho

            if nuevo_y1 < 0:
                nuevo_y1 = 0
                nuevo_y2 = alto

            if nuevo_x2 > img_w:
                nuevo_x2 = img_w
                nuevo_x1 = img_w - ancho

            if nuevo_y2 > img_h:
                nuevo_y2 = img_h
                nuevo_y1 = img_h - alto

            self.crop = [nuevo_x1, nuevo_y1, nuevo_x2, nuevo_y2]

        else:
            if self.drag_mode == "tl":
                x1 = min(max(0, x), x2 - MIN_CROP_SIZE)
                y1 = min(max(0, y), y2 - MIN_CROP_SIZE)

            elif self.drag_mode == "tr":
                x2 = max(min(img_w, x), x1 + MIN_CROP_SIZE)
                y1 = min(max(0, y), y2 - MIN_CROP_SIZE)

            elif self.drag_mode == "bl":
                x1 = min(max(0, x), x2 - MIN_CROP_SIZE)
                y2 = max(min(img_h, y), y1 + MIN_CROP_SIZE)

            elif self.drag_mode == "br":
                x2 = max(min(img_w, x), x1 + MIN_CROP_SIZE)
                y2 = max(min(img_h, y), y1 + MIN_CROP_SIZE)

            self.crop = [x1, y1, x2, y2]

        self.last_mouse = (event.x, event.y)
        self.dibujar_crop()

    def mouse_up(self, event):
        self.drag_mode = None
        self.last_mouse = None

    def aceptar(self):
        if not self.img_original or not self.crop:
            return

        salida = nombre_salida(self.path_actual)

        if salida.exists():
            respuesta = messagebox.askyesno(
                "Archivo existente",
                f"Ya existe:\n{salida.name}\n\n¿Quieres sobrescribirlo?"
            )
            if not respuesta:
                return

        try:
            x1, y1, x2, y2 = self.crop

            ox1 = int(x1 / self.scale)
            oy1 = int(y1 / self.scale)
            ox2 = int(x2 / self.scale)
            oy2 = int(y2 / self.scale)

            ox1 = max(0, ox1)
            oy1 = max(0, oy1)
            ox2 = min(self.img_original.width, ox2)
            oy2 = min(self.img_original.height, oy2)

            recortada = self.img_original.crop((ox1, oy1, ox2, oy2))

            ext = salida.suffix.lower()

            if ext in [".jpg", ".jpeg"]:
                recortada.save(
                    salida,
                    quality=95,
                    subsampling=0,
                    optimize=True
                )
            else:
                recortada.save(salida)

            self.procesadas.add(self.path_actual.name)
            guardar_estado({
                "procesadas": sorted(self.procesadas)
            })

            self.indice += 1
            self.cargar_siguiente()

        except Exception as e:
            messagebox.showerror(
                "Error al guardar",
                f"No se pudo guardar la imagen:\n\n{e}"
            )

    def saltar(self):
        self.indice += 1
        self.cargar_siguiente()

    def salir(self):
        guardar_estado({
            "procesadas": sorted(self.procesadas)
        })
        self.root.destroy()


# ================= EJECUCIÓN =================

if __name__ == "__main__":
    root = tk.Tk()
    app = Reencuadrador(root)
    root.mainloop()