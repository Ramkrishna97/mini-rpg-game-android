using System;
using System.IO;
using UnityEngine;

// Art-only exporter. Each boss has the same 8-direction, 4-clip contract:
// idle, move, attack, death; four 32px frames per clip. Nothing here affects AI.
public static class EmberfallBossArtBuilder
{
    public const int Cell = 32;
    public const int Columns = 16;
    public const int Rows = 8;
    public static readonly string[] Names = { "gloomfang", "stonewarden", "mirewitch", "ashtitan", "hollowking", "dragon" };
    private static Texture2D sheet;
    private static int ox, oy, face, bob, action, phase, kind;

    public static void BuildAll(string output)
    {
        Directory.CreateDirectory(output);
        for (kind = 0; kind < Names.Length; kind++)
        {
            sheet = new Texture2D(Cell * Columns, Cell * Rows, TextureFormat.RGBA32, false, false);
            sheet.filterMode = FilterMode.Point;
            var clear = new Color[sheet.width * sheet.height];
            sheet.SetPixels(clear);
            for (var direction = 0; direction < Rows; direction++)
            for (var frame = 0; frame < Columns; frame++)
            {
                ox = frame * Cell;
                oy = (Rows - direction - 1) * Cell;
                action = frame / 4;
                phase = frame % 4;
                face = direction >= 5 ? -1 : direction >= 1 && direction <= 3 ? 1 : 0;
                bob = action == 1 ? new[] { 0, -1, 0, 1 }[phase] : action == 0 ? new[] { 0, 0, -1, 0 }[phase] : 0;
                Draw(direction);
            }
            sheet.Apply(false, false);
            File.WriteAllBytes(Path.Combine(output, Names[kind] + ".png"), sheet.EncodeToPNG());
            UnityEngine.Object.DestroyImmediate(sheet);
        }
    }

    private static Color C(string hex) { ColorUtility.TryParseHtmlString("#" + hex, out var color); return color; }
    private static void Dot(int x, int y, Color c)
    {
        // Author in screen coordinates; invert inside the Texture2D tile.
        if (x >= 0 && x < Cell && y >= 0 && y < Cell) sheet.SetPixel(ox + x, oy + Cell - y - 1, c);
    }
    private static void Box(int x, int y, int w, int h, Color c)
    { for (int py = y; py < y + h; py++) for (int px = x; px < x + w; px++) Dot(px, py, c); }
    private static void Ellipse(int cx, int cy, int rx, int ry, Color c)
    {
        for (int y = -ry; y <= ry; y++) for (int x = -rx; x <= rx; x++)
            if (x*x*ry*ry + y*y*rx*rx <= rx*rx*ry*ry) Dot(cx+x, cy+y, c);
    }
    private static void Line(int x0, int y0, int x1, int y1, Color c, int width = 1)
    {
        int steps = Math.Max(Math.Abs(x1-x0), Math.Abs(y1-y0));
        for (int i = 0; i <= steps; i++)
        {
            int x = x0 + (x1-x0)*i/Math.Max(1, steps);
            int y = y0 + (y1-y0)*i/Math.Max(1, steps);
            for (int dy = 0; dy < width; dy++) for (int dx = 0; dx < width; dx++) Dot(x+dx, y+dy, c);
        }
    }
    private static void Draw(int direction)
    {
        // The back three rows hide eyes; flank rows move facial details sideways.
        bool back = direction == 0 || direction == 1 || direction == 7;
        int drop = action == 3 ? phase * 3 : 0;
        if (action == 3 && phase == 3) { Remnant(); return; }
        switch (kind)
        {
            case 0: Armadillo(back, drop); break;
            case 1: Vulture(back, drop); break;
            case 2: Mole(back, drop); break;
            case 3: Tiger(back, drop); break;
            case 4: Wolf(back, drop); break;
            default: Dragon(back, drop); break;
        }
    }
    private static void Remnant()
    {
        string[] colors = { "665E58", "737B80", "584936", "783F30", "9FAEB9", "5A322D" };
        Ellipse(16, 26, 10, 3, C(colors[kind]));
        if (kind == 3 || kind == 5) { Dot(9, 22, C("FF8A32")); Dot(23, 23, C("FF8A32")); }
    }
    private static void Armadillo(bool back, int drop)
    {
        bool roll = action == 2 && phase >= 1;
        int cy = 17 + bob + drop;
        Box(6, 23 + drop, 5, 5, C("433D46")); Box(22, 23 + drop, 5, 5, C("433D46"));
        Ellipse(16, cy, roll ? 11 : 12, roll ? 10 : 8, C("292B36"));
        Ellipse(16, cy-1, roll ? 10 : 11, roll ? 9 : 7, C("676777"));
        // Staggered shell plates and ivory spines communicate the roll/fan attacks.
        for (int i = 0; i < 4; i++)
        {
            int x = 7 + i*5;
            Line(x, cy-6, x+2, cy+3, C("A69B91"));
            Line(x+2, cy-8, x+3, cy-11-(i%2), C("E7D4B3"), 2);
        }
        if (!roll) { Ellipse(16 + face*6, cy+5, 5, 4, C("857671")); Ellipse(16 + face*7, cy+7, 3, 2, C("CAB6A3")); }
        if (!back && !roll) { Dot(13+face*6, cy+4, C("FFD276")); Dot(18+face*6, cy+4, C("FFD276")); }
        if (action == 2 && phase == 0) { Line(5, 16, 1, 12, C("E7D4B3")); Line(27, 16, 30, 12, C("E7D4B3")); }
    }
    private static void Vulture(bool back, int drop)
    {
        int cy = 15 + bob + drop;
        int spread = action == 2 ? 2 + phase*2 : action == 1 ? phase%2*2 : 0;
        Line(14, cy+7, 11, 28, C("3F4248"), 2); Line(19, cy+7, 22, 28, C("3F4248"), 2);
        Line(11, 27, 6, 28, C("D8D6C7")); Line(22, 27, 27, 28, C("D8D6C7"));
        Ellipse(16, cy+3, 7, 9, C("31363D"));
        for (int side = -1; side <= 1; side += 2)
        {
            Line(16+side*5, cy, 16+side*(13+spread), cy-7-spread/2, C("D2D5D5"), 4);
            Line(16+side*(11+spread), cy-6-spread/2, 16+side*(15+spread), cy+7, C("636D76"), 3);
        }
        Ellipse(16+face*2, cy-6, 5, 5, C("E1DDD0"));
        Ellipse(16+face*3, cy-7, 4, 3, C("A84F48"));
        if (!back) { Dot(14+face*4, cy-7, C("FFE484")); Dot(18+face*4, cy-7, C("FFE484")); }
        Line(15+face*3, cy-3, 16+face*5, cy+1, C("E8D9A9"), 2);
    }
    private static void Mole(bool back, int drop)
    {
        int cy = 18 + bob + drop;
        if (action == 2 && phase >= 2) cy += 4;
        Ellipse(16, cy, 12, 9, C("352F2C")); Ellipse(16, cy-2, 11, 7, C("6A513E"));
        for (int i = 0; i < 5; i++) { Dot(7+i*4, cy-8+i%2, C("80A069")); Dot(9+i*4, cy-9+i%2, C("73875A")); }
        Ellipse(7, cy+6, 6, 4, C("8C7864")); Ellipse(25, cy+6, 6, 4, C("8C7864"));
        for (int i = 0; i < 3; i++) { Line(3+i*3, cy+6, 1+i*3, cy+11, C("E8DAB7"), 2); Line(24+i*3, cy+6, 26+i*3, cy+11, C("E8DAB7"), 2); }
        if (!back) { Ellipse(16+face*3, cy+1, 5, 4, C("B09A83")); Ellipse(16+face*4, cy+3, 3, 2, C("DFAFAD")); }
        if (action == 2) { Dot(4, 27, C("AA8150")); Dot(28, 27, C("AA8150")); }
    }
    private static void Tiger(bool back, int drop)
    {
        int stride = action == 1 ? new[] {0,2,0,-2}[phase] : 0;
        int leap = action == 2 && phase >= 1 ? -3 : 0;
        int cy = 18 + bob + drop + leap;
        Line(24, cy+1, 30, cy-4, C("CC6840"), 3); Dot(30, cy-5, C("FFB343"));
        Box(7, cy+3+stride, 5, 8, C("522D29")); Box(21, cy+3-stride, 5, 8, C("522D29"));
        Ellipse(16, cy, 11, 7, C("3B3130")); Ellipse(16, cy-1, 10, 6, C("C77846"));
        for (int i = 0; i < 5; i++) Line(7+i*4, cy-6, 10+i*3, cy+2, C("342B2E"), 2);
        Ellipse(14+face*4, cy-5, 6, 5, C("D99153"));
        Box(8+face*4, cy-11, 3, 4, C("332D2D")); Box(18+face*4, cy-11, 3, 4, C("332D2D"));
        if (!back) { Dot(12+face*4, cy-5, C("FFF0AD")); Dot(17+face*4, cy-5, C("FFF0AD")); }
        for (int i = 0; i < 4; i++) { Line(7+i*5, cy-8, 8+i*5, cy-11-(i%2)*2, C("FF8B32"), 2); Dot(8+i*5, cy-12-(i%2)*2, C("FFE276")); }
        if (action == 2) { Dot(7, 29, C("FF742C")); Dot(25, 29, C("FF742C")); }
    }
    private static void Wolf(bool back, int drop)
    {
        int cy = 18 + bob + drop;
        int reach = action == 2 ? phase : 0;
        Line(23, cy+2, 30, cy-4, C("D8E1E4"), 4);
        Box(7-reach, cy+3, 4, 8, C("B0C1CB")); Box(21+reach, cy+3, 4, 8, C("B0C1CB"));
        Ellipse(16, cy, 11, 7, C("687D91")); Ellipse(16, cy-2, 10, 6, C("D8E3E8"));
        for (int i = 0; i < 4; i++) Line(7+i*6, cy-5, 6+i*6, cy-10-i%2*2, C("91DCFF"), 2);
        Ellipse(14+face*4, cy-6, 6, 6, C("F0F1EB"));
        Line(9+face*4, cy-9, 8+face*4, cy-16, C("E2E9ED"), 3);
        Line(19+face*4, cy-9, 21+face*4, cy-16, C("E2E9ED"), 3);
        for (int i = 0; i < 3; i++) Line(11+i*3+face*4, cy-12, 12+i*3+face*4, cy-19-(i==1?2:0), C("A8E7FF"), 2);
        if (!back) { Dot(12+face*4, cy-6, C("75DFFF")); Dot(17+face*4, cy-6, C("75DFFF")); }
        if (action == 2 && phase >= 2) { Dot(3, 10, C("83DFFF")); Dot(28, 9, C("83DFFF")); }
    }
    private static void Dragon(bool back, int drop)
    {
        int cy = 19 + bob + drop;
        int spread = action == 2 ? phase*2 : action == 1 ? phase%2*2 : 0;
        Line(22, cy+3, 30, cy+8, C("482B2F"), 3); Dot(30, cy+8, C("FF8638"));
        for (int side = -1; side <= 1; side += 2)
        {
            Line(16+side*5, cy-3, 16+side*(13+spread), cy-12, C("4A3034"), 5);
            Line(16+side*5, cy-3, 16+side*(13+spread), cy-12, C("A64F35"), 2);
        }
        Box(9, cy+4, 4, 7, C("392B2C")); Box(20, cy+4, 4, 7, C("392B2C"));
        Ellipse(16, cy, 8, 9, C("342B2E")); Ellipse(16, cy+1, 5, 6, C("B65334"));
        Line(14, cy-2, 16, cy+6, C("FF9B3E"), 2); Line(19, cy-2, 17, cy+6, C("FF9B3E"), 2);
        Ellipse(15+face*2, cy-8, 5, 5, C("754035"));
        Line(12+face*2, cy-11, 10+face*2, cy-17, C("E8C292"), 2);
        Line(18+face*2, cy-11, 21+face*2, cy-17, C("E8C292"), 2);
        if (!back) { Dot(13+face*2, cy-8, C("FFB347")); Dot(18+face*2, cy-8, C("FFB347")); }
        if (action == 2) { Line(15+face*3, cy-4, 16+face*4, cy-7, C("FFE075"), 3); }
    }
}
