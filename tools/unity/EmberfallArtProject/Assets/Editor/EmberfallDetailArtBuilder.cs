using System;
using System.IO;
using UnityEngine;

/// <summary>Pixel art authored in Unity's Texture2D space. Rows are top down in the PNG.</summary>
public static class EmberfallDetailArtBuilder
{
    private static Texture2D sheet;
    private static int ox, oy, cell;
    private static Color C(string hex) { ColorUtility.TryParseHtmlString("#" + hex, out var color); return color; }

    public static void BuildAll(string output)
    {
        Directory.CreateDirectory(output);
        Begin(384, 256, 32);
        for (int direction = 0; direction < 8; direction++)
        for (int frame = 0; frame < 12; frame++) { Cell(frame, direction); Player(direction, frame); }
        Save(Path.Combine(output, "player.png"));

        Begin(256, 96, 32);
        for (int item = 0; item < 19; item++) { Cell(item % 8, item / 8); Item(item); }
        Save(Path.Combine(output, "items.png"));

        Begin(192, 480, 48);
        for (int species = 0; species < 10; species++)
        for (int frame = 0; frame < 4; frame++) { Cell(frame, species); Wildlife(species, frame); }
        Save(Path.Combine(output, "wildlife.png"));

        Begin(128, 32, 32);
        for (int frame = 0; frame < 4; frame++) { Cell(frame, 0); Chest(frame); }
        Save(Path.Combine(output, "chests.png"));
    }

    private static void Begin(int width, int height, int size)
    {
        cell = size;
        sheet = new Texture2D(width, height, TextureFormat.RGBA32, false, false);
        sheet.filterMode = FilterMode.Point;
        sheet.SetPixels(new Color[width * height]);
    }

    private static void Cell(int column, int row) { ox = column * cell; oy = sheet.height - (row + 1) * cell; }
    private static void Save(string path)
    {
        sheet.Apply(false, false);
        File.WriteAllBytes(path, sheet.EncodeToPNG());
        UnityEngine.Object.DestroyImmediate(sheet);
    }

    private static void P(int x, int y, Color color)
    {
        if (x >= 0 && x < cell && y >= 0 && y < cell) sheet.SetPixel(ox + x, oy + cell - y - 1, color);
    }
    private static void R(int x, int y, int w, int h, Color color)
    { for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) P(xx, yy, color); }
    private static void E(int x, int y, int rx, int ry, Color color)
    {
        for (int yy = -ry; yy <= ry; yy++) for (int xx = -rx; xx <= rx; xx++)
            if (xx * xx * ry * ry + yy * yy * rx * rx <= rx * rx * ry * ry) P(x + xx, y + yy, color);
    }
    private static void L(int x0, int y0, int x1, int y1, Color color, int width = 1)
    {
        int steps = Math.Max(1, Math.Max(Math.Abs(x1 - x0), Math.Abs(y1 - y0)));
        for (int i = 0; i <= steps; i++)
        {
            int x = x0 + (x1 - x0) * i / steps, y = y0 + (y1 - y0) * i / steps;
            R(x, y, width, width, color);
        }
    }
    private static void Gem(int x, int y, Color rim, Color core)
    {
        for (int row = 0; row < 14; row++)
        {
            int half = row < 5 ? 1 + row : row < 10 ? 6 : Math.Max(1, 6 - (row - 9));
            R(x - half, y + row, half * 2 + 1, 1, rim);
        }
        R(x - 2, y + 3, 4, 8, core); P(x - 3, y + 5, C("FFFFFF"));
    }

    private static void Player(int direction, int frame)
    {
        int action = frame / 4, phase = frame % 4;
        bool back = direction == 0 || direction == 1 || direction == 7;
        int facing = direction >= 5 ? -1 : direction >= 1 && direction <= 3 ? 1 : 0;
        int bob = action == 1 ? new[] { 0, -2, 0, -1 }[phase] : action == 0 ? new[] { 0, 0, -1, 0 }[phase] : 0;
        int stride = action == 1 ? new[] { -2, 1, 2, -1 }[phase] : 0;
        var outline = C("172430"); var leather = C("70462E"); var steel = C("B7C6CF");
        var tunic = C("304A63"); var gold = C("D7A65F"); var skin = C("DFAE84");
        E(16, 29, 8, 2, new Color(0, 0, 0, .3f));
        // Separated boots make the walk cycle read even at a small scale.
        R(10 + stride, 23 + bob, 5, 6, outline); R(18 - stride, 23 + bob, 5, 6, outline);
        R(11 + stride, 23 + bob, 3, 5, leather); R(19 - stride, 23 + bob, 3, 5, leather);
        R(10, 12 + bob, 13, 13, outline); R(11, 12 + bob, 11, 11, tunic);
        R(10, 20 + bob, 13, 2, gold); R(14, 20 + bob, 3, 2, steel);
        R(11, 13 + bob, 3, 6, steel); R(19, 13 + bob, 3, 6, steel);
        // Paired shoulder plates and a cape distinguish front and back rows.
        E(10, 14 + bob, 3, 4, outline); E(23, 14 + bob, 3, 4, outline);
        R(8, 12 + bob, 4, 4, steel); R(22, 12 + bob, 4, 4, steel);
        if (back) { R(13, 13 + bob, 7, 10, C("27384D")); L(15, 13 + bob, 20, 21 + bob, gold); }
        E(16, 9 + bob, 6, 6, outline);
        E(16, 9 + bob, 5, 5, back ? C("343D4C") : skin);
        R(11, 3 + bob, 10, 4, C("2C303A")); R(10, 6 + bob, 3, 4, C("2C303A"));
        R(14, 4 + bob, 4, 2, C("687684"));
        if (!back) { P(14 + facing, 9 + bob, outline); P(18 + facing, 9 + bob, outline); }
        if (action == 2)
        {
            int reach = new[] { 1, 4, 7, 3 }[phase];
            int side = facing == 0 ? 1 : facing;
            L(23, 15 + bob, 22 + side * reach, 12 + bob - reach / 2, leather, 2);
            L(22 + side * reach, 12 + bob - reach / 2, 22 + side * (reach + 5), 6 + bob - reach / 2, steel, 2);
            P(22 + side * (reach + 5), 5 + bob - reach / 2, C("F9F3D6"));
            if (phase == 2) { L(25, 5, 29, 11, C("7CD9F3")); L(26, 6, 30, 10, C("E8FAFF")); }
        }
        else { R(7, 16 + bob, 3, 6, leather); R(23, 16 + bob, 3, 6, leather); }
    }

    // Item rows follow ItemId ordinal order, so the same atlas drives inventory and ground loot.
    private static void Item(int index)
    {
        var dark = C("262C36"); var silver = C("CFDEE4"); var glint = C("FFFFFF");
        if (index == 0) // wood
        {
            L(5, 23, 24, 9, dark, 7); L(6, 22, 24, 10, C("9B6536"), 5);
            L(8, 21, 24, 10, C("D5A166")); E(5, 23, 3, 3, C("E8BB79")); E(25, 9, 3, 3, C("E8BB79"));
            return;
        }
        if (index == 1 || index == 3) // stone / iron ore
        {
            var baseColor = C(index == 1 ? "7D8C97" : "765B48");
            E(16, 21, 11, 7, dark); E(16, 19, 10, 7, baseColor);
            E(12, 15, 6, 4, C(index == 1 ? "B9C8CE" : "D5B17A"));
            R(18, 17, 5, 4, C(index == 1 ? "52616D" : "B98A55")); P(11, 13, glint); return;
        }
        if (index == 5) { Gem(16, 6, C("553C95"), C("B89BF8")); return; }
        if (index == 2) { for (int i = 0; i < 5; i++) L(9 + i * 3, 26, 7 + i * 3, 9, C("9DB86C"), 2); return; }
        if (index == 4) { E(16, 18, 11, 8, C("9B674B")); E(16, 18, 8, 5, C("C99069")); return; }
        if (index >= 9 && index <= 12) // four distinct potions
        {
            string[] liquid = { "43D6C9", "6689F5", "E89546", "6BD47C" };
            E(16, 19, 8, 10, dark); E(16, 18, 7, 9, C("B8D3D7"));
            E(16, 21, 6, 6, C(liquid[index - 9])); R(13, 4, 6, 7, dark);
            R(12, 4, 8, 3, C("D8BB7D")); R(11, 10, 10, 2, silver);
            L(12, 15, 12, 22, glint, 2); P(18, 16, glint); return;
        }
        if (index >= 13 && index <= 16) // sword, cross sword, ring blade
        {
            if (index == 16)
            {
                E(16, 15, 10, 10, dark); E(16, 15, 8, 8, C("D5B857")); E(16, 15, 5, 5, dark);
                P(16, 4, glint); R(12, 25, 8, 4, C("704B34")); return;
            }
            L(6, 27, 23, 7, dark, 5);
            L(7, 26, 23, 7, C(index == 13 ? "A16C40" : "C7D6DF"), 3);
            L(11, 24, 24, 7, glint);
            L(7, 17, 17, 24, C(index == 15 ? "EBC879" : "B79154"), 3);
            L(6, 28, 12, 21, C("704B34"), 3); return;
        }
        if (index == 17) // bow
        {
            for (int y = 5; y < 27; y++) { int x = 10 + Math.Abs(y - 16) / 3; P(x, y, C("A4713C")); }
            L(13, 5, 13, 27, silver); L(10, 16, 27, 16, C("D8BD82"), 2);
            L(23, 13, 28, 16, silver); L(23, 19, 28, 16, silver); return;
        }
        if (index == 18) // flintlock
        {
            R(5, 12, 21, 5, dark); R(6, 13, 18, 3, silver); R(9, 17, 5, 11, C("815833"));
            R(24, 11, 5, 7, C("677785")); R(15, 9, 5, 3, C("D5AE59")); return;
        }
        // food
        if (index == 6) { E(16, 18, 9, 9, C("D85759")); E(13, 14, 4, 3, C("F38E7D")); L(16, 9, 18, 5, C("73502B"), 2); E(21, 7, 4, 2, C("70B45C")); }
        else if (index == 7 || index == 8) { E(16, 18, 11, 7, C(index == 8 ? "B97A40" : "B65D5B")); R(22, 17, 6, 3, silver); E(28, 18, 2, 3, silver); }
    }

    private static void Wildlife(int species, int frame)
    {
        int bob = new[] { 0, -1, 0, 1 }[frame];
        string[] coats = { "DCCDBA", "A9784F", "745943", "829BA5", "C56C38", "9B8066", "587E91", "7D7772", "EBE7D8", "EACD72" };
        var coat = C(coats[species]); var outline = C("263341");
        E(24, 39, 17, 3, new Color(0, 0, 0, .25f));
        if (species == 6) // shark, swimming silhouette and moving tail
        {
            E(24, 28 + bob, 17, 7, C("365C70")); E(25, 26 + bob, 14, 6, coat);
            L(14, 26, 10, 17, C("365C70"), 3); L(10, 17, 20, 26, coat, 2);
            L(7, 28, 2, 19 + frame, coat, 3); L(7, 28, 2, 37 - frame, coat, 3);
            R(26, 30 + bob, 14, 2, C("D9E3E4")); P(34, 24 + bob, C("142631"));
            L(13, 38, 31, 38, C("91D2E0")); return;
        }
        int bodyY = 27 + bob;
        E(21, bodyY + 2, 15, species == 8 ? 11 : 8, outline);
        E(21, bodyY, 14, species == 8 ? 10 : 8, coat);
        int leg = frame == 1 ? 2 : frame == 3 ? -2 : 0;
        R(13 + leg, 31 + bob, 4, 8, outline); R(29 - leg, 31 + bob, 4, 8, outline);
        if (species == 0) { E(12, 20 + bob, 6, 7, coat); E(15, 10 + bob, 2, 10, coat); E(22, 9 + bob, 2, 11, coat); }
        if (species == 4 || species == 5) { L(10, 27, 2, 16 + frame, coat, 5); E(3, 16 + frame, 4, 3, species == 4 ? C("EEE5CE") : coat); }
        if (species == 8) for (int i = 0; i < 5; i++) E(10 + i * 5, 20 + (i % 2) * 5 + bob, 5, 5, C("FBF5E9"));
        if (species == 1) { L(30, 16, 29, 7, C("9EBA8D"), 2); L(36, 16, 37, 7, C("9EBA8D"), 2); }
        if (species == 2) { E(6, 27, 5, 5, coat); R(33, 18, 7, 6, coat); }
        if (species == 3) { E(8, 21 + bob, 7, 5, coat); L(8, 19, 6, 11 + frame, coat, 2); }
        if (species == 9) { E(8, 23, 6, 6, C("F3DB86")); R(8, 15, 4, 6, C("CE6948")); }
        E(35, 23 + bob, species == 0 ? 5 : 7, 6, outline);
        E(35, 22 + bob, species == 0 ? 5 : 6, 5, species == 8 ? C("544B48") : coat);
        if (species == 4) { L(31, 17, 29, 10, coat, 3); L(38, 17, 39, 10, coat, 3); R(34, 26, 7, 2, C("F7E9D4")); }
        if (species == 0) { R(32, 18, 2, 7, C("E9ACB2")); }
        P(38, 21 + bob, C("15222B")); P(41, 25 + bob, outline);
    }

    private static void Chest(int frame)
    {
        var dark = C("2A2321"); var wood = C("84542E"); var light = C("C68848");
        E(16, 29, 14, 2, new Color(0, 0, 0, .3f));
        R(3, 13, 26, 17, dark); R(5, 15, 22, 13, wood);
        R(7, 18, 18, 2, light); R(6, 25, 20, 2, C("5D3C2A"));
        R(8, 14, 3, 14, C("657783")); R(22, 14, 3, 14, C("657783"));
        int lift = frame == 0 ? 0 : frame == 1 ? 3 : 7;
        if (frame >= 2) { R(6, 10, 20, 5, C("F4C76D")); R(8, 11, 16, 3, C("FBE7A6")); }
        R(3, 8 - lift, 26, 9, dark); R(5, 8 - lift, 22, 7, light);
        R(8, 8 - lift, 3, 7, C("667887")); R(22, 8 - lift, 3, 7, C("667887"));
        if (frame == 0) { R(14, 16, 5, 7, C("E8C66E")); R(16, 18, 1, 2, dark); }
        if (frame == 3) { P(4, 4, C("FFF2B0")); P(29, 8, C("FFF2B0")); P(16, 3, C("FFF2B0")); }
    }
}
