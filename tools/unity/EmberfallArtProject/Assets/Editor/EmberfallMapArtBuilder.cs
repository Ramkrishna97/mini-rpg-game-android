using System;
using System.IO;
using UnityEngine;

// Builds the four new terrain strips used by the island and themed boss arenas.
// Each strip contains four 128px variants, matching TileAtlas's tile contract.
public static class EmberfallMapArtBuilder
{
    private const int Cell = 128;
    private static readonly string[] Names = { "snow", "ash", "lava", "arena_stone" };

    public static void BuildAll()
    {
        string[] args = Environment.GetCommandLineArgs();
        string output = null;
        for (int i = 0; i + 1 < args.Length; i++)
            if (args[i] == "-emberfallOutput") output = args[i + 1];
        if (string.IsNullOrEmpty(output)) throw new ArgumentException("Missing -emberfallOutput");
        Directory.CreateDirectory(output);

        for (int kind = 0; kind < Names.Length; kind++)
        {
            var texture = new Texture2D(Cell * 4, Cell, TextureFormat.RGBA32, false);
            texture.filterMode = FilterMode.Point;
            for (int variant = 0; variant < 4; variant++)
            {
                var random = new System.Random(7301 + kind * 7919 + variant * 293);
                for (int y = 0; y < Cell; y++)
                for (int x = 0; x < Cell; x++)
                {
                    float grain = (float)random.NextDouble();
                    Color color;
                    switch (kind)
                    {
                        case 0: // snow drifts and glints
                            color = grain < .12f ? Hex("BBD6E4") : grain > .93f ? Hex("F8FDFF") : Hex("DCECF3");
                            if ((x + 2 * y + variant * 17) % 61 < 2) color = Hex("C5DEE9");
                            break;
                        case 1: // cooled ash with glowing cracks
                            color = grain < .19f ? Hex("251F28") : grain > .89f ? Hex("5B4140") : Hex("392D33");
                            if (Math.Abs((y + 2 * variant + (int)(9 * Math.Sin(x * .075f))) % 51) < 2)
                                color = Hex("A85535");
                            break;
                        case 2: // molten stream between dark plates
                            float seam = Math.Abs(((y + 5 * variant + (int)(12 * Math.Sin(x * .042f))) % 48) - 24);
                            color = seam < 3 ? Hex("FFD369") : seam < 7 ? Hex("FF8B37") :
                                seam < 11 ? Hex("B94725") : grain > .87f ? Hex("662D2A") : Hex("40242A");
                            break;
                        default: // cut arena flagstones and a fine circular inscription
                            color = grain < .08f ? Hex("677F8A") : grain > .91f ? Hex("B8CFD5") : Hex("91AAB2");
                            if (x % 32 < 2 || y % 32 < 2) color = Hex("526771");
                            int cx = x % 32 - 16, cy = y % 32 - 16;
                            int radius = cx * cx + cy * cy;
                            if (radius > 70 && radius < 87) color = Hex("C7DFE3");
                            break;
                    }
                    texture.SetPixel(variant * Cell + x, Cell - 1 - y, color);
                }
            }
            texture.Apply(false);
            File.WriteAllBytes(Path.Combine(output, Names[kind] + ".png"), texture.EncodeToPNG());
            UnityEngine.Object.DestroyImmediate(texture);
        }
        Debug.Log("Emberfall map terrain exported to " + output);
    }

    private static Color Hex(string value)
    {
        ColorUtility.TryParseHtmlString("#" + value, out var color);
        return color;
    }
}
