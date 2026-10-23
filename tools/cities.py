"""Convierte cities5000.txt de GeoNames en una tabla chica para la app (nombre, país, lat, lon)."""
import sys

src, dst = sys.argv[1], sys.argv[2]
n = 0
with open(src, encoding="utf-8") as f, open(dst, "w", encoding="utf-8") as out:
    for line in f:
        p = line.rstrip("\n").split("\t")
        if len(p) < 15:
            continue
        name, lat, lon, fcode, cc = p[1], p[4], p[5], p[7], p[8]
        if fcode in ("PPLX", "PPLH", "PPLQ", "PPLW"):  # barrios y lugares abandonados/históricos
            continue
        out.write(f"{name}\t{cc}\t{float(lat):.4f}\t{float(lon):.4f}\n")
        n += 1
print(f"{n} ciudades")
if n < 10000:
    sys.exit("Muy pocas ciudades: la descarga salió mal")
