# Approved launcher artwork integration

Use only the verified owner-supplied complete PNG. Preserve the image without separating or repainting its glass, sand or petals.

- Store the exact source in drawable-nodpi. Generate legacy density PNGs from that source.
- Use a complete-artwork adaptive background and transparent foreground. Compensate for the platform's enlarged layer viewport to preserve the original composition at rest.
- Keep Manifest icon references and notification resources. Remove the old launcher monochrome reference. A designed monochrome asset remains pending.
- Render the packaged adaptive layers under circle, rounded-square and squircle masks, plus the platform default and 48dp samples, on the host.
- Run Debug tests, Debug build and diff checks. Keep previews in ignored build reports. Device behavior and owner acceptance remain separate.
