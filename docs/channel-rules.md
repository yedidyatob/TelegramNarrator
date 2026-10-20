# Per-channel cleaning rules

Telegram channels often end their posts with things that should not be read aloud ("12 תגובות",
"כדי להגיב לכתבה לחצו כאן", links to the post itself, ads...). The rules engine removes them
**before** the generic `MessageCleaner` and the text-to-speech run. Chats without a matching
preset are cleaned by the `default` preset and `MessageCleaner`. **No link is ever read aloud by default**
(`readLinks: false`, `removeTelegramLinks: true`); the generic `MessageCleaner` also removes URLs when the
rules file cannot be loaded.

The rules live in `app/src/main/assets/channel_rules.json` and are loaded once when playback starts
(`ChannelRulesRepository`). If the file is missing or invalid the error is logged under the tag
`ChannelRules` and no rules are applied; the app keeps working.

## Pipeline

For every unread message of a chat, in the order they are played:

1. **Drop decision** (on the original text): `dropMessage` rules decide whether the
   message is read at all. Each message is decided on its own text only. A dropped message is not spoken, but it is still **marked as read**, in order,
   through the normal read-checkpoint flow, like a message that was heard.
2. **Text rules** for messages that are read: `cut` -> `replace` -> `removeTelegramLinks` ->
   links (`readLinks` / `linkLabel`) -> `stripSymbols` -> `numberedLists`.
3. The generic `MessageCleaner` (also used for chat titles and sponsored messages):
   - any URL that is still left is removed; markdown markers are stripped;
   - repeated punctuation is collapsed: `!!!` -> `!`, `???` -> `?`, `....` / `. . .` / `…` -> `.`,
     `----` / `====` / `~~~~` / `••••` runs -> a space, a standalone `##` / `###` run is removed
     (`##tag` -> `#tag`); single punctuation and meaningful symbols (`.,?!:;`, `"` / `'` in רע"מ / צה"ל,
     `%`, `₪`, `$`, `+`, `-`, parentheses, `12:30`, `1/2`) are kept;
   - hashtags are read as words (`#foo_bar` -> `# foo bar`); emoji are removed;
   - lines without a letter or digit (`##`, `---`, `***`, `•••`, emoji-only) are dropped, and the remaining
     lines are joined with a pause: `, ` after a line that does not end in punctuation, `. ` before a
     paragraph break (blank or dropped line), so lists are not read as one run-on phrase;
   - whitespace is collapsed.

If the rules leave nothing to say (e.g. the message was only "12 תגובות"), the message is skipped
and marked as read, like any other empty message.

## File format

```jsonc
{
  "version": 1,

  // Used for every chat that no channel matches.
  "default": {
    "rules":   { /* same keys as a channel, see below */ },
    "options": { /* same keys as a channel */ }
  },

  // Checked in order, the first matching channel wins.
  "channels": [
    {
      "name": "Abu Ali Express",              // only used in logs / error messages
      "match": {                              // a chat matches if ANY of these matches
        "chatIds": [-1001234567890],
        "titleEquals": "אבו עלי אקספרס",        // case-insensitive, trimmed
        "titleContains": "אבו עלי"              // case-insensitive
      },
      "inheritDefault": true,                 // optional, default true (see "Inheritance")

      "rules": {
        "dropMessage": [ "<regex>", ... ],
        "cut":         [ "<regex>", ... ],
        "replace":     [ { "pattern": "<regex>", "replacement": "text or $1" }, ... ]
      },

      "options": {
        "readLinks": true,                   // the default preset has false: links are not read
        "linkLabel": "קישור",                // only used when readLinks is true
        "removeTelegramLinks": true,
        "numberedLists": "keep",
        "stripSymbols": ["°"]
      }
    }
  ]
}
```

`match` needs at least one of `chatIds`, `titleEquals`, `titleContains`. Everything else is optional.

### Rules

All patterns are Java/Kotlin regular expressions, compiled with **MULTILINE** (`^`/`$` match at the
start/end of every line). Use `\A` / `\z` for the start/end of the whole message, and inline flags such
as `(?i)` (ignore case) or `(?s)` (dot matches newlines). Remember that backslashes must be doubled in JSON:
`"\\d+ תגובות"`.

| Rule | Meaning |
|------|---------|
| `dropMessage` | A message whose text matches **any** pattern is not read (but marked read). Example: `"(?i)givechak\\.co\\.il"`. |
| `cut` | Every match is deleted from the text. Example: trailing comment counts `"^[ \\t]*(?:\\d+[ \\t]+(?:תגובות|תגובה)|תגובה[ \\t]+אחת)[ \\t]*$"` (covers `N תגובות`, `1 תגובה`, and `תגובה אחת`). |
| `replace` | Every match of `pattern` is replaced by `replacement` (Java syntax: `$1` is group 1, write `\\$` for a literal dollar). |

Messages are matched on their text or caption; media without a caption has an empty text.

**No "skip the next message" rule.** There used to be a `dropNext` rule (a marker message drops the
message(s) after it), used to skip a channel's own ad posts. It was removed (#96): channels' own ad posts are
read aloud like any other message, to support channel owners. A `dropNext` key in the JSON is ignored.
Telegram's official sponsored messages are a separate feature and are unaffected.

### Options

| Option | Values | Default | Meaning |
|--------|--------|---------|---------|
| `readLinks` | `true` / `false` | `false` | **Links are not read by default**: `false` removes URLs completely (no "link" word). `true` reads every URL as `linkLabel`. |
| `linkLabel` | string | the localized word for "Link" | What a URL is read as (only when `readLinks` is `true`). |
| `removeTelegramLinks` | `true` / `false` | `true` | Removes `t.me`, `telegram.me`, `telegram.dog` links entirely, including suffixes like `?single`. This matters when `readLinks` is `true` (to read other links but never Telegram ones); with `readLinks: false` every link is removed anyway. To remove the links of one channel only, use a `cut` rule such as `"(?i)(?:https?://)?t\\.me/mychannel\\S*"`. |
| `numberedLists` | `"keep"` / `"strip"` / `"natural"` | `"keep"` | Handling of list numbers at the start of a line (`1.` / `2)`): `strip` removes them, `natural` turns `1. text` into `1, text` so it is read as a number with a pause. |
| `stripSymbols` | list of strings | `[]` | Literal strings removed from the text, e.g. `["°"]`. |

### Inheritance

A channel starts from the `default` preset: its `dropMessage` / `cut` / `replace` lists and
`stripSymbols` are placed **before** the channel's own, and a channel only needs to set the options it wants to
change. Put `"inheritDefault": false` to ignore the default completely.

## Adding a channel

1. Copy an entry in `channels`, set `match` (the exact title as shown in Telegram is easiest).
2. Add the `cut` / `dropMessage` rules for the junk at the start/end of its posts.
3. Add a unit test: put sample messages under `app/src/test/resources/channel_rules/<channel>/`
   and assert the cleaned output like `ChannelRulesEngineTest` does (the test parses the shipped
   `channel_rules.json`, so mistakes in the JSON are caught by `ChannelRulesParserTest` too).

Invalid JSON, an invalid regex or an unknown `numberedLists` value makes the **whole file** fail
to load (and everything falls back to the generic cleaner); the log message names the channel and the
pattern.

## Example: Abu Ali Express

See the `Abu Ali Express` entry in `channel_rules.json` and the reconstructed sample messages in
`app/src/test/resources/channel_rules/abu_ali/`: it reads the channel's own `°תוכן שיווקי` posts like any message
(the `°` is stripped; no ad skipping since #96), drops `givechak` / donation links, cuts `N תגובות` / `1 תגובה` / `תגובה אחת` and `כדי להגיב לכתבה לחצו כאן`, drops all
`t.me` links (via the default `removeTelegramLinks` / `readLinks: false`), reads list numbers naturally and removes `°`.
