---
name: kodosi-canvas
description: "Show the user an image, a file, a website or the app that you run, on the canvas beside this Kodosi terminal, and use the shared browser there. Use it when the user asks to see, open or preview a thing, when a table, a plan with choices, a comparison or a diagram is easier to see than to read, and when you need a browser yourself to open, test, click through or take a picture of a web page."
---

# Kodosi canvas

Each Kodosi terminal has a canvas beside it: a panel for files and one shared browser. The
user sees it on each device where they watch the terminal, and so do the people that they
share the terminal with. They see what you do there as you do it: your pointer in the
browser, and your edits in a document in your name. They can click, type and edit in the
same place at the same time.

## Show

- `kodosi show <file>`: puts one or more files on the canvas and opens the canvas for the
  user at the first, also when they closed it. When you change a file later, the canvas
  shows the new version, so show a file one time.
- `kodosi show <address>`: opens a website, or the app that you run, in the shared browser
  on the canvas, for example `kodosi show http://localhost:5173`.
- `kodosi show browser`: opens the canvas at the shared browser as it is, without a new
  load of its page. Use it after your work in the browser, when the user must look.
- After you show a thing, say in one line what it is. Do not ask the user to open a file
  or an address themselves.
- What the canvas shows: HTML, Markdown and text files as pages (Markdown and text get the
  look of Kodosi), pictures (PNG, JPEG, GIF, WebP, SVG), and documents (.docx), sheets
  (.xlsx), presentations (.pptx) and PDFs, which people edit there
  (`kodosi skill documents`).
- An HTML page runs its own scripts, but it loads nothing from the network and opens no
  other file. Put its styles, scripts and pictures into the page, with inline SVG or
  data: addresses. A page that loads from the network opens in the shared browser.
- `kodosi skill page` prints a page with the look of Kodosi, in light and dark. Start from
  it for a plan with choices, a comparison or a report, and mark your recommendation.
- `kodosi canvas` lists what the canvas has. `kodosi canvas remove <name>` takes one item
  off, and `kodosi canvas remove --all` all files. The canvas keeps no copy of a file, and
  taking it off deletes nothing: deleting the file is a separate step.
- The user can select words on the canvas and send them to you, with the file or the
  address that they come from.

## Shared browser

`kodosi browser <command>` runs an agent-browser command in the browser of this terminal.
The browser starts at the first command. The user sees your work there while the canvas
is open, and `kodosi show browser` opens it.

- `kodosi browser open https://example.com`: goes to an address.
- `kodosi browser snapshot -i`: the parts of the page that you can use, each with a
  reference such as `@e2`. A reference is good until the page changes. Take a new
  snapshot after each change, and when Kodosi says that a person used the browser.
  In PowerShell, write a reference without the @ sign (`e2`), because PowerShell drops
  a word that starts with @.
- `kodosi browser click @e2`, `kodosi browser fill @e3 "text"`,
  `kodosi browser press Enter` and `kodosi browser scroll down` use the page.
- `kodosi browser get text @e1` and `kodosi browser get url` read it.
- `kodosi browser screenshot shot.png`: saves a picture.
- `kodosi browser upload @e4 report.pdf`: gives a file to a file field. A file that a
  page downloads goes into the folder of this terminal and shows on the canvas.
- `kodosi browser wait --load networkidle`, `kodosi browser back`,
  `kodosi browser tab new https://example.com` and `kodosi browser tab close`.
  `kodosi browser --help` lists each command.
- The browser sleeps after 10 minutes without use and wakes with the same sign-ins.
- When a page asks for a sign-in, ask the user to sign in on the canvas. Never ask for a
  password, and never type one that you found.
