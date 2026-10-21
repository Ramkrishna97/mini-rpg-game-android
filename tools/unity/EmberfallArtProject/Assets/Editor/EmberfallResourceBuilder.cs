using System;
using System.IO;
using UnityEditor;
using UnityEngine;

/// <summary>
/// Generates Emberfall's first replaceable art pack without requiring any Unity
/// packages. Artists can later replace these PNGs with hand-painted exports,
/// provided the dimensions and grid contract remain unchanged.
/// </summary>
public static class EmberfallResourceBuilder
{
    private const int AtlasSize = 1024;
    private const int TileSize = 128;
    private const int TileColumns = 8;
    private const int SpriteCell = 32;
    private const int SpriteColumns = 12;
    private const int SpriteRows = 8;

    [MenuItem("Emberfall/Build Runtime Art Pack")]
    public static void BuildAll()
    {
        var output = Path.Combine(EmberfallOutputRoot(), "art");
        Directory.CreateDirectory(output);

        BuildTileset(Path.Combine(output, "tileset.png"));
        EmberfallDetailArtBuilder.BuildAll(output);
        EmberfallBossArtBuilder.BuildAll(output);
        ApplyOverrides(output);

        AssetDatabase.Refresh();
        Debug.Log("Emberfall art exported to " + output);
    }

    // PNGs in the Unity project's Assets/EmberfallArtOverrides folder take
    // precedence. Artists can replace art without changing Android code or AI.
    private static void ApplyOverrides(string output)
    {
        var source = Path.Combine(Application.dataPath, "EmberfallArtOverrides");
        if (!Directory.Exists(source)) return;
        foreach (var name in new[] { "tileset", "player", "items", "wildlife", "chests",
                 "gloomfang", "stonewarden", "mirewitch", "ashtitan", "hollowking", "dragon" })
        {
            var path = Path.Combine(source, name + ".png");
            if (!File.Exists(path)) continue;
            var bytes = File.ReadAllBytes(path);
            var image = new Texture2D(2, 2);
            try
            {
                if (!image.LoadImage(bytes, true)) throw new InvalidDataException("Invalid PNG: " + path);
                var width = name == "tileset" ? 1024 : name == "player" ? 384 :
                    name == "items" ? 256 : name == "wildlife" ? 192 : name == "chests" ? 128 : 512;
                var height = name == "tileset" ? 1024 : name == "items" ? 96 :
                    name == "wildlife" ? 480 : name == "chests" ? 32 : 256;
                if (image.width != width || image.height != height)
                    throw new InvalidDataException(name + " must be " + width + "x" + height);
                File.WriteAllBytes(Path.Combine(output, name + ".png"), bytes);
            }
            finally { UnityEngine.Object.DestroyImmediate(image); }
        }
    }

    private static string EmberfallOutputRoot()
    {
        var args = Environment.GetCommandLineArgs();
        for (var i = 0; i < args.Length - 1; i++)
            if (args[i] == "-emberfallOutput") return Path.GetFullPath(args[i + 1]);

        // Allows the self-contained sample project in this repository to work
        // without arguments, while real Unity projects use -emberfallOutput.
        return Path.GetFullPath(Path.Combine(Application.dataPath, "../../../content-pack/generated"));
    }

    private static void BuildTileset(string path)
    {
        var texture = new Texture2D(AtlasSize, AtlasSize, TextureFormat.RGBA32, false, false);
        texture.filterMode = FilterMode.Bilinear;
        texture.wrapMode = TextureWrapMode.Clamp;
        Clear(texture, new Color(0f, 0f, 0f, 0f));

        for (var type = 0; type < 12; type++)
        for (var variant = 0; variant < 4; variant++)
        {
            var index = type * 4 + variant;
            // Texture2D coordinates start at the bottom; Android decodes PNGs
            // from the top, so invert rows to preserve the runtime atlas order.
            PaintTile(texture, (index % TileColumns) * TileSize,
                AtlasSize - ((index / TileColumns) + 1) * TileSize, type, variant);
        }
        WritePng(texture, path);
    }

    // Must retain the TileType enum order in Emberfall's Kotlin code.
    private static void PaintTile(Texture2D texture, int originX, int originY, int type, int variant)
    {
        var bases = new[]
        {
            Hex("477D45"), Hex("334D36"), Hex("4E8046"), Hex("80613C"),
            Hex("D4B46D"), Hex("2879A4"), Hex("16425F"), Hex("477D45"),
            Hex("527461"), Hex("65616B"), Hex("4A4752"), Hex("334D36")
        };
        var baseColor = bases[type];
        for (var y = 0; y < TileSize; y++)
        for (var x = 0; x < TileSize; x++)
        {
            var grain = FractalNoise(x + type * 91, y + variant * 157);
            var shade = Mathf.Lerp(0.78f, 1.18f, grain);
            if (type == 5 || type == 6) shade = Mathf.Lerp(0.72f, 1.12f, grain);
            texture.SetPixel(originX + x, originY + y, baseColor * shade);
        }

        var random = new System.Random(type * 1009 + variant * 7919);
        switch (type)
        {
            case 0: case 2: ScatterGrass(texture, originX, originY, random, type == 2); break;
            case 1: ScatterLeaves(texture, originX, originY, random); break;
            case 3: ScatterPebbles(texture, originX, originY, random, Hex("4A3423"), Hex("AE875A")); break;
            case 4: ScatterPebbles(texture, originX, originY, random, Hex("957643"), Hex("F2D99B")); break;
            case 5: case 6: DrawWater(texture, originX, originY, random, type == 6); break;
            case 7: DrawTree(texture, originX, originY, random, false); break;
            case 8: DrawRock(texture, originX, originY, random); break;
            case 9: DrawRuinedFloor(texture, originX, originY, random); break;
            case 10: DrawRuinWall(texture, originX, originY, random); break;
            case 11: DrawTree(texture, originX, originY, random, true); break;
        }
    }

    private static void ScatterGrass(Texture2D texture, int x, int y, System.Random random, bool flowers)
    {
        for (var i = 0; i < 68; i++)
        {
            var px = x + random.Next(4, 124); var py = y + random.Next(4, 124);
            var color = i % 3 == 0 ? Hex("285B35") : Hex("77A950");
            Line(texture, px, py, px + random.Next(-2, 3), py + random.Next(3, 8), color, 1);
        }
        if (!flowers) return;
        var petals = new[] { Hex("FFD166"), Hex("F7A6C2"), Hex("F9F6E7") };
        for (var i = 0; i < 15; i++)
        {
            var px = x + random.Next(8, 120); var py = y + random.Next(8, 120);
            FillCircle(texture, px, py, 2, petals[i % petals.Length]);
            Set(texture, px, py, Hex("E19834"));
        }
    }

    private static void ScatterLeaves(Texture2D texture, int x, int y, System.Random random)
    {
        var colors = new[] { Hex("67432D"), Hex("87633A"), Hex("B47D45"), Hex("264C32") };
        for (var i = 0; i < 75; i++)
        {
            var px = x + random.Next(3, 124); var py = y + random.Next(3, 124);
            FillEllipse(texture, px, py, random.Next(2, 5), random.Next(1, 3), colors[i % colors.Length]);
        }
    }

    private static void ScatterPebbles(Texture2D texture, int x, int y, System.Random random, Color dark, Color light)
    {
        for (var i = 0; i < 36; i++)
        {
            var px = x + random.Next(3, 121); var py = y + random.Next(3, 121);
            FillEllipse(texture, px, py, random.Next(1, 4), random.Next(1, 3), i % 4 == 0 ? light : dark);
        }
    }

    private static void DrawWater(Texture2D texture, int x, int y, System.Random random, bool deep)
    {
        var light = deep ? Hex("3E7797") : Hex("8BD3E6");
        for (var i = 0; i < 9; i++)
        {
            var py = y + 9 + i * 13 + random.Next(-3, 4);
            var px = x + random.Next(-9, 25);
            Line(texture, px, py, px + random.Next(30, 66), py, light, 2);
            Line(texture, px + 10, py + 3, px + random.Next(24, 48), py + 3, light * 0.65f, 1);
        }
    }

    private static void DrawTree(Texture2D texture, int x, int y, System.Random random, bool forest)
    {
        FillEllipse(texture, x + 64, y + 97, 40, 13, new Color(0f, 0f, 0f, 0.18f));
        FillRect(texture, x + 56, y + 55, 16, 55, Hex("58371F"));
        FillRect(texture, x + 60, y + 55, 5, 54, Hex("B27642"));
        var dark = forest ? Hex("173D32") : Hex("235636");
        var mid = forest ? Hex("286044") : Hex("367A43");
        var light = forest ? Hex("4F8651") : Hex("76AE56");
        FillCircle(texture, x + 64, y + 49, 43, dark);
        FillCircle(texture, x + 46, y + 59, 31, mid);
        FillCircle(texture, x + 80, y + 56, 33, mid);
        FillCircle(texture, x + 55, y + 38, 24, light);
        FillCircle(texture, x + 78, y + 36, 17, light * 0.9f);
        for (var i = 0; i < 16; i++)
            FillCircle(texture, x + random.Next(25, 103), y + random.Next(22, 76), random.Next(2, 5), light * 1.12f);
    }

    private static void DrawRock(Texture2D texture, int x, int y, System.Random random)
    {
        FillEllipse(texture, x + 65, y + 100, 40, 12, new Color(0f, 0f, 0f, 0.2f));
        FillEllipse(texture, x + 64, y + 72, 39, 34, Hex("44565D"));
        FillEllipse(texture, x + 58, y + 64, 27, 22, Hex("738B91"));
        for (var i = 0; i < 12; i++)
            FillCircle(texture, x + random.Next(32, 96), y + random.Next(48, 95), random.Next(1, 4), Hex("2B3941"));
    }

    private static void DrawRuinedFloor(Texture2D texture, int x, int y, System.Random random)
    {
        var grout = Hex("302F39");
        for (var row = 0; row < 4; row++)
        for (var col = 0; col < 4; col++)
        {
            var ox = x + col * 32 + (row % 2) * 5;
            var oy = y + row * 32;
            FillRect(texture, ox + 2, oy + 2, 27, 27, grout);
            FillRect(texture, ox + 4, oy + 4, 23, 23, Hex("706B74") * (0.82f + random.Next(0, 20) / 100f));
        }
    }

    private static void DrawRuinWall(Texture2D texture, int x, int y, System.Random random)
    {
        for (var row = 0; row < 7; row++)
        {
            var offset = row % 2 == 0 ? 0 : -18;
            for (var col = 0; col < 4; col++)
            {
                var px = x + offset + col * 38;
                var py = y + row * 19;
                FillRect(texture, px + 1, py + 1, 35, 16, Hex("272936"));
                FillRect(texture, px + 3, py + 3, 31, 12, Hex("625E6B") * (0.78f + random.Next(0, 24) / 100f));
            }
        }
    }

    private static void BuildSpriteSheet(string path, int character)
    {
        var texture = new Texture2D(SpriteColumns * SpriteCell, SpriteRows * SpriteCell, TextureFormat.RGBA32, false, false);
        texture.filterMode = FilterMode.Point;
        Clear(texture, new Color(0f, 0f, 0f, 0f));
        for (var direction = 0; direction < SpriteRows; direction++)
        for (var frame = 0; frame < SpriteColumns; frame++)
            DrawCharacter(texture, frame * SpriteCell, (SpriteRows - direction - 1) * SpriteCell, character, direction, frame);
        WritePng(texture, path);
    }

    private static void DrawCharacter(Texture2D texture, int ox, int oy, int character, int direction, int frame)
    {
        var palettes = new[]
        {
            new[] { Hex("2D3558"), Hex("74B9D6"), Hex("F4D29A"), Hex("E7EEF7") },
            new[] { Hex("202931"), Hex("87B55B"), Hex("E5C087"), Hex("E8E8E0") },
            new[] { Hex("39424C"), Hex("8296A1"), Hex("C6D2D9"), Hex("5C6C78") },
            new[] { Hex("4E315E"), Hex("A97BCB"), Hex("E5C6A7"), Hex("F0D95C") },
            new[] { Hex("5B302A"), Hex("CB6544"), Hex("F1A164"), Hex("FFD28A") },
            new[] { Hex("29283D"), Hex("705B9B"), Hex("D6D1DF"), Hex("C8A1DF") }
        };
        var colors = palettes[character];
        var action = frame / 4; // idle, walk, attack
        var phase = frame % 4;
        var bob = action == 1 ? (phase % 2 == 0 ? 1 : -1) : 0;
        var facing = 0;
        if (direction >= 1 && direction <= 3) facing = 1;
        if (direction >= 5) facing = -1;
        var scale = character == 0 ? 1 : 1 + character / 4;
        var cx = ox + 16 + facing;
        var ground = oy + 27 + bob;
        FillEllipse(texture, cx, ground, 9 * scale, 2, new Color(0f, 0f, 0f, 0.33f));
        FillRect(texture, cx - 5 * scale, ground - 14 * scale, 10 * scale, 12 * scale, colors[0]);
        FillCircle(texture, cx, ground - 18 * scale, 6 * scale, colors[2]);
        FillCircle(texture, cx, ground - 21 * scale, 6 * scale, colors[1]);
        FillRect(texture, cx - 4 * scale, ground - 10 * scale, 8 * scale, 4 * scale, colors[1]);
        Set(texture, cx + facing * 3, ground - 19 * scale, colors[3]);
        if (action == 2)
        {
            var reach = 8 + phase * 2;
            Line(texture, cx + facing * 3, ground - 12 * scale, cx + facing * reach, ground - 18 * scale, colors[3], 2);
            Line(texture, cx + facing * reach, ground - 18 * scale, cx + facing * (reach + 5), ground - 22 * scale, Hex("FFF1C1"), 1);
        }
        else
        {
            FillRect(texture, cx - 4 * scale, ground - 2 * scale, 3 * scale, 4 * scale, colors[0] * 0.7f);
            FillRect(texture, cx + 1 * scale, ground - 2 * scale, 3 * scale, 4 * scale, colors[0] * 0.7f);
        }
    }

    private static float FractalNoise(float x, float y)
    {
        var n = Mathf.PerlinNoise(x / 41f, y / 41f) * .55f;
        n += Mathf.PerlinNoise(x / 15f + 13f, y / 15f + 29f) * .3f;
        n += Mathf.PerlinNoise(x / 5f + 47f, y / 5f + 71f) * .15f;
        return n;
    }

    private static Color Hex(string value)
    {
        ColorUtility.TryParseHtmlString("#" + value, out var color);
        return color;
    }

    private static void Clear(Texture2D texture, Color color)
    {
        var pixels = new Color[texture.width * texture.height];
        for (var i = 0; i < pixels.Length; i++) pixels[i] = color;
        texture.SetPixels(pixels);
    }

    private static void WritePng(Texture2D texture, string path)
    {
        texture.Apply(false, false);
        File.WriteAllBytes(path, texture.EncodeToPNG());
        UnityEngine.Object.DestroyImmediate(texture);
    }

    private static void Set(Texture2D texture, int x, int y, Color color)
    {
        if (x >= 0 && x < texture.width && y >= 0 && y < texture.height) texture.SetPixel(x, y, color);
    }

    private static void FillRect(Texture2D texture, int x, int y, int width, int height, Color color)
    {
        for (var py = y; py < y + height; py++)
        for (var px = x; px < x + width; px++) Set(texture, px, py, color);
    }

    private static void FillCircle(Texture2D texture, int x, int y, int radius, Color color) => FillEllipse(texture, x, y, radius, radius, color);

    private static void FillEllipse(Texture2D texture, int x, int y, int radiusX, int radiusY, Color color)
    {
        for (var py = -radiusY; py <= radiusY; py++)
        for (var px = -radiusX; px <= radiusX; px++)
            if (px * px * radiusY * radiusY + py * py * radiusX * radiusX <= radiusX * radiusX * radiusY * radiusY)
                Set(texture, x + px, y + py, color);
    }

    private static void Line(Texture2D texture, int x0, int y0, int x1, int y1, Color color, int width)
    {
        var steps = Mathf.Max(Mathf.Abs(x1 - x0), Mathf.Abs(y1 - y0));
        for (var i = 0; i <= steps; i++)
        {
            var x = Mathf.RoundToInt(Mathf.Lerp(x0, x1, i / (float)steps));
            var y = Mathf.RoundToInt(Mathf.Lerp(y0, y1, i / (float)steps));
            FillCircle(texture, x, y, Mathf.Max(0, width - 1), color);
        }
    }
}
