import math
import struct
import zlib

WIDTH = 512
HEIGHT = 512

def create_png(width, height, rgba_data, filename):
    # PNG signature
    png = b'\x89PNG\r\n\x1a\n'
    
    # IHDR chunk
    # width (4), height (4), bit depth (1 byte, 8), color type (1 byte, 6=RGBA),
    # compression (1 byte, 0), filter (1 byte, 0), interlace (1 byte, 0)
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    ihdr_crc = zlib.crc32(b'IHDR' + ihdr_data)
    png += struct.pack('>I', len(ihdr_data)) + b'IHDR' + ihdr_data + struct.pack('>I', ihdr_crc)
    
    # Raw scanlines with filter byte 0 (None)
    raw_lines = bytearray()
    for y in range(height):
        raw_lines.append(0)  # filter type 0
        row_start = y * width * 4
        raw_lines.extend(rgba_data[row_start : row_start + width * 4])
        
    compressed = zlib.compress(bytes(raw_lines), 9)
    idat_crc = zlib.crc32(b'IDAT' + compressed)
    png += struct.pack('>I', len(compressed)) + b'IDAT' + compressed + struct.pack('>I', idat_crc)
    
    # IEND chunk
    iend_crc = zlib.crc32(b'IEND')
    png += struct.pack('>I', 0) + b'IEND' + struct.pack('>I', iend_crc)
    
    with open(filename, 'wb') as f:
        f.write(png)
    print(f"Successfully generated {filename} ({width}x{height} PNG)")

def clamp(val, min_v=0, max_v=255):
    return max(min_v, min(max_v, int(val)))

def blend_pixel(bg, fg):
    # fg: (r, g, b, a [0.0 - 1.0])
    # bg: [r, g, b, a (0 - 255)]
    fg_a = fg[3]
    if fg_a <= 0.0:
        return
    bg_a = bg[3] / 255.0
    out_a = fg_a + bg_a * (1.0 - fg_a)
    if out_a <= 0:
        return
    bg[0] = clamp((fg[0] * fg_a + bg[0] * bg_a * (1.0 - fg_a)) / out_a)
    bg[1] = clamp((fg[1] * fg_a + bg[1] * bg_a * (1.0 - fg_a)) / out_a)
    bg[2] = clamp((fg[2] * fg_a + bg[2] * bg_a * (1.0 - fg_a)) / out_a)
    bg[3] = clamp(out_a * 255)

def main():
    cx, cy = 256.0, 256.0
    pixels = bytearray(WIDTH * HEIGHT * 4)

    # Rounded squircle boundary mask
    corner_radius = 110.0

    for y in range(HEIGHT):
        for x in range(WIDTH):
            idx = (y * WIDTH + x) * 4
            
            # Squircle distance check
            dx = abs(x - cx)
            dy = abs(y - cy)
            
            # Distance from center
            dist = math.hypot(x - cx, y - cy)
            angle = math.atan2(y - cy, x - cx) # -pi to pi

            # 1. Base dark tactical navy gradient
            radial_t = min(1.0, dist / 340.0)
            # Center: #10243C (16, 36, 60), Edge: #040811 (4, 8, 17)
            bg_r = int(16 * (1.0 - radial_t) + 4 * radial_t)
            bg_g = int(38 * (1.0 - radial_t) + 8 * radial_t)
            bg_b = int(62 * (1.0 - radial_t) + 17 * radial_t)
            
            cur_pixel = [bg_r, bg_g, bg_b, 255]

            # 2. Outer rounded border highlight (Squircle boundary)
            # Distance in rounded rect
            qx = max(0.0, dx - (256.0 - corner_radius))
            qy = max(0.0, dy - (256.0 - corner_radius))
            corner_dist = math.hypot(qx, qy)
            is_outside = corner_dist > corner_radius
            
            if is_outside:
                pixels[idx:idx+4] = bytes([0, 0, 0, 0])
                continue

            border_margin = corner_radius - corner_dist
            if border_margin < 4.0:
                # Border glow
                border_alpha = max(0.0, min(1.0, border_margin / 4.0)) * 0.8
                blend_pixel(cur_pixel, (0, 229, 255, border_alpha * 0.7))

            # 3. Concentric Doppler Radar rings (radii: 90, 150, 205)
            for ring_r, ring_alpha, width_px in [(90, 0.45, 2.0), (150, 0.35, 2.0), (205, 0.28, 2.5)]:
                d_ring = abs(dist - ring_r)
                if d_ring < width_px:
                    alpha = (1.0 - (d_ring / width_px)) * ring_alpha
                    blend_pixel(cur_pixel, (0, 229, 255, alpha))

            # 4. Crosshairs axes (horizontal & vertical)
            if dist < 225.0:
                d_axis = min(abs(x - cx), abs(y - cy))
                if d_axis < 1.2:
                    dash = (int(dist) // 6) % 2
                    if dash == 0:
                        blend_pixel(cur_pixel, (0, 229, 255, 0.3))

            # 5. Radar sweep beam (Sweep in top right, angle between -pi/2 and 0)
            if -math.pi/2 <= angle <= 0 and dist < 210.0:
                # Normalizing angle: from -pi/2 (0.0) to 0 (1.0)
                sweep_t = (angle - (-math.pi/2)) / (math.pi/2)
                sweep_alpha = (sweep_t ** 2.2) * 0.45 * (1.0 - (dist / 215.0) * 0.3)
                blend_pixel(cur_pixel, (0, 229, 255, sweep_alpha))
                
                # Sweeper leading sharp edge at angle = 0 (horizontal right)
                if abs(y - cy) < 2.5 and x > cx:
                    lead_alpha = (1.0 - abs(y - cy)/2.5) * 0.85
                    blend_pixel(cur_pixel, (0, 229, 255, lead_alpha))

            # 6. Cyclone / Tropical Storm Vortex Dual Arms
            # Arm 1: Center around angle = 0.8*pi + log spiral
            # Polar equation for hurricane spiral: theta = theta_0 + c * ln(r)
            if 30.0 < dist < 190.0:
                # Log spiral angle
                spiral_ref1 = (2.2 * math.log(dist / 30.0)) - 0.5
                diff1 = (angle - spiral_ref1) % (2 * math.pi)
                if diff1 > math.pi:
                    diff1 -= 2 * math.pi
                diff1 = abs(diff1)

                spiral_ref2 = spiral_ref1 + math.pi
                diff2 = (angle - spiral_ref2) % (2 * math.pi)
                if diff2 > math.pi:
                    diff2 -= 2 * math.pi
                diff2 = abs(diff2)

                arm_width = 0.52 * (1.0 - (dist / 200.0) * 0.4)
                
                # Arm 1: Vibrant Alert Red/Crimson (#FF3B30 to #FF2D55)
                if diff1 < arm_width:
                    t1 = 1.0 - (diff1 / arm_width)
                    alpha1 = (t1 ** 1.5) * 0.95
                    # Color blend: Red to Amber
                    c_r = 255
                    c_g = int(59 * (1.0 - t1) + 149 * t1)
                    c_b = 48
                    blend_pixel(cur_pixel, (c_r, c_g, c_b, alpha1))

                # Arm 2: Vibrant Amber/Gold (#FF9500 to #FFCC00)
                if diff2 < arm_width:
                    t2 = 1.0 - (diff2 / arm_width)
                    alpha2 = (t2 ** 1.5) * 0.92
                    c_r = 255
                    c_g = int(149 * (1.0 - t2) + 204 * t2)
                    c_b = 0
                    blend_pixel(cur_pixel, (c_r, c_g, c_b, alpha2))

            # 7. Storm Core & Calm Eye
            if dist < 42.0:
                # Storm wall glow
                wall_t = max(0.0, 1.0 - abs(dist - 28.0) / 14.0)
                blend_pixel(cur_pixel, (255, 60, 48, wall_t * 0.85))

            if dist < 22.0:
                # Calm dark eye center
                blend_pixel(cur_pixel, (6, 13, 25, 0.95))
                # Eye border ring
                if abs(dist - 20.0) < 1.8:
                    blend_pixel(cur_pixel, (255, 204, 0, 0.9))

            # 8. Bright Cyan Center Beacon
            if dist < 10.0:
                core_t = max(0.0, 1.0 - (dist / 10.0))
                blend_pixel(cur_pixel, (0, 229, 255, core_t * 0.95))
            if dist < 4.0:
                blend_pixel(cur_pixel, (255, 255, 255, 0.98))

            # 9. North Compass Indicator Marker (Triangle at top)
            if 34 <= y <= 48 and abs(x - cx) <= (48 - y) * 0.5:
                blend_pixel(cur_pixel, (0, 229, 255, 0.95))

            pixels[idx : idx + 4] = bytes(cur_pixel)

    create_png(WIDTH, HEIGHT, pixels, "icon_512x512.png")
    # Also copy to res/drawable and app root
    with open("icon_512x512.png", "rb") as f_in:
        content = f_in.read()
    with open("app/src/main/res/drawable/icon_512x512.png", "wb") as f_out:
        f_out.write(content)
    with open("app/icon_512x512.png", "wb") as f_out:
        f_out.write(content)
    print("Saved to icon_512x512.png, app/src/main/res/drawable/icon_512x512.png, and app/icon_512x512.png")

if __name__ == "__main__":
    main()
