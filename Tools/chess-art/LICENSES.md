# AvraChess artwork

Piece images in `TMessagesProj/src/main/res/drawable-nodpi/chess_*.webp` are rendered by
`make_sets.py` from SVG sets published in the lichess repository (public/piece/<set>):

| Avragram name | Source set | Author | License |
| --- | --- | --- | --- |
| Classic, Neon, Gold & steel, Own colors | cburnett | Colin M.L. Burnett | GPLv2+ |
| Merida | merida | Armando Hernandez Marroquin | GPLv2+ |
| Chessnut | chessnut | Alexis Luengas | Apache 2.0 |
| Fantasy, Spatial, Celtic | fantasy, spatial, celtic | Maurizio Monge | MIT |
| Papercut | papercut | Nikolay Anzarov | CC BY 4.0 |
| Candy | rhosgfx | RhosGFX | CC0 1.0 |
| Minimal | totoy | Kosal Sen | CC BY 4.0 |

Neon, Gold & steel and Own colors are recolored versions of cburnett made by `make_sets.py`.
All images were rasterized to 192 px WebP with a soft drop shadow.

Sounds in `res/raw/chess_{wood,marble,marimba}_*.ogg` are synthesized by `make_sounds.py`
(numpy, no recordings) and are part of Avragram.
