# Per-channel cleaning rules

Telegram channels often end their posts with things that should not be read aloud ("12 תגובות",
"כדי להגיב לכתבה לחצו כאן", links to the post itself, ads...). The rules engine removes them
**before** the generic `MessageCleaner` and the text-to-speech run. Chats without a matching
preset are cleaned by `MessageCleaner` only, exactly as before.

The rules live in `app/src/main/assets/channel_rules.json` and are loaded once when playback starts
(`ChannelRulesRepository`). If the file is missing or invalid the error is logged under the tag
`ChannelRules` and no rules are applied; the app keeps working.

## Pipeline

For every unread message of a chat, in the order they are played:

1. **Drop decision** (on the original text): `dropMessage` and `dropNext` rules decide whether the
   message is read at all. A dropped message is not spoken, but it is still **marked as read**, in order,
   through the normal read-checkpoint flow, like a message that was heard.
2. **Text rules** for messages that are read: `cut` -> `replace` -> `removeTelegramLinks` ->
   links (`readLinks` / `linkLabel`) -> `stripSymbols` -> `numberedLists`.
3. The generic `MessageCleaner` (markdown, emoji, the remaining links read as "Link", whitespace).

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
        "dropNext":    [ { "pattern": "<regex>", "count": 1 }, ... ],
        "cut":         [ "<regex>", ... ],
        "replace":     [ { "pattern": "<regex>", "replacement": "text or $1" }, ... ]
      },

      "options": {
        "readLinks": true,
        "linkLabel": "קישור",
        "removeTelegramLinks": false,
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
| `dropNext` | A message matching `pattern` (the *marker*) is dropped **and so are the next `count` messages** (default 1) in the order they are played. Markers inside a dropped group extend it. Make the pattern match *only* the marker message when the ad can also be in the same message, e.g. `"\\A\\s*°\\s*תוכן שיווקי\\s*\\z"` matches a message that contains nothing else, so an ad that already contains its own marker doesn't swallow the next real message (use `dropMessage` for that case). |
| `cut` | Every match is deleted from the text. Example: trailing comment counts `"^[ \\t]*\\d+ (?:תגובות\|תגובה)[ \\t]*$"`. |
| `replace` | Every match of `pattern` is replaced by `replacement` (Java syntax: `$1` is group 1, write `\\$` for a literal dollar). |

Messages are matched on their text or caption; media without a caption has an empty text.

**Groups that span two runs.** At most 100 unread messages are queued per chat and run. If a
`dropNext` group is cut off by the end of the batch **and the chat has more unread messages**, the marker
(and anything after it in the batch) is *deferred*: it is not read and **not** marked read, so the
next run sees the marker together with its ad. If nothing follows at all, the marker is simply dropped and
marked; an ad that arrives later is then only caught by `dropMessage` rules, so keep a rule for the ad
itself (like the `givechak` one) as a backstop.

### Options

| Option | Values | Default | Meaning |
|--------|--------|---------|---------|
| `readLinks` | `true` / `false` | `true` | `false` removes URLs completely instead of reading "link". |
| `linkLabel` | string | the localized word for "Link" | What a URL is read as (when `readLinks` is true). |
| `removeTelegramLinks` | `true` / `false` | `false` | Removes `t.me`, `telegram.me`, `telegram.dog` links entirely, including suffixes like `?single`. For one channel only, use a `cut` rule such as `"(?i)(?:https?://)?t\\.me/mychannel\\S*"`. |
| `numberedLists` | `"keep"` / `"strip"` / `"natural"` | `"keep"` | Handling of list numbers at the start of a line (`1.` / `2)`): `strip` removes them, `natural` turns `1. text` into `1, text` so it is read as a number with a pause. |
| `stripSymbols` | list of strings | `[]` | Literal strings removed from the text, e.g. `["°"]`. |

### Inheritance

A channel starts from the `default` preset: its `dropMessage` / `dropNext` / `cut` / `replace` lists and
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
`app/src/test/resources/channel_rules/abu_ali/`: it drops `°תוכן שיווקי` ads (inline, or as a separate marker
message followed by the ad) and `givechak` / donation links, cuts `N תגובות`, `כדי להגיב לכתבה לחצו כאן` and
`t.me/abualiexpress/...` links, reads list numbers naturally and removes `°`.
