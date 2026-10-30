# Contributing to Illusion

Questions go in the [Discord](https://discord.com/invite/rzgm3C8YWu) or an issue.

## Building

You need Git and Java 25.

```sh
git clone https://codeberg.org/awruff/Illusion.git
cd Illusion
./gradlew build
```

The jar ends up in `build/libs/`. Run `./gradlew runClient` to launch a development client.

## Code style

Most editors, including IntelliJ IDEA, pick up the [`.editorconfig`](.editorconfig):

- Tabs for Java and Gradle files.
- 2 spaces for JSON and `.properties` files.
- LF line endings and a trailing newline.

Otherwise, match the surrounding code.

## Commits

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <short description>
```

Common types are `feat`, `fix`, `refactor`, `docs`, and `chore`. For example:

```
fix: keep forced sneak when another mod overrides the sneak key
```

Sign every commit with GPG or SSH ([setup guide](https://docs.codeberg.org/security/gpg-key/)).

## Pull requests

- One change per PR.
- Target `master`.
- CI must pass before merging, so run `./gradlew build` first.

## License

Illusion is licensed under the [GPL-3.0-or-later](LICENSE) with the Minecraft linking exception at the end of that file. Contributions are accepted under the same license, exception included.
