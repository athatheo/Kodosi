use std::collections::{BTreeMap, HashMap};

use document::Assets;
use serde_json::Value;
use text::{Mark, Node};

use crate::{apply::block_id, model::schema, write::new_picture};

/// The most columns that a table cell spans: as many as HTML lets a view draw a cell across.
pub(crate) const MOST_SPAN: u64 = 1000;

/// What a document holds that its tree names: the pieces of its file and the bytes of pictures.
pub(crate) struct Held<'a> {
    pub(crate) raws: &'a HashMap<String, String>,
    /// Pieces that come with the merge of a write of another program.
    pub(crate) keep: &'a BTreeMap<String, String>,
    pub(crate) assets: &'a Assets,
}

impl Held<'_> {
    fn piece(&self, owner: &str, key: Option<&Value>) -> Result<(), String> {
        match key.and_then(Value::as_str) {
            Some(key) if self.raws.contains_key(key) || self.keep.contains_key(key) => Ok(()),
            Some(key) => Err(format!(
                "The {owner} names the piece {key} of the file, which the document does not hold."
            )),
            None => Err(format!(
                "The {owner} needs the piece of the file that it stands for."
            )),
        }
    }

    fn properties(&self, owner: &str, key: Option<&Value>) -> Result<(), String> {
        match key {
            None | Some(Value::Null) => Ok(()),
            key => self.piece(owner, key),
        }
    }

    fn picture(&self, asset: Option<&Value>) -> Result<(), String> {
        match asset.and_then(Value::as_str) {
            Some(asset) if new_picture(self.assets, asset).is_some() => Ok(()),
            _ => Err("A new picture needs a PNG, JPEG or GIF that the document holds.".to_owned()),
        }
    }
}

/// Refuses a change after which the writer could not save the document.
pub(crate) fn writable_change(before: &Node, after: &Node, held: &Held<'_>) -> Result<(), String> {
    let kept: HashMap<&str, &Node> = before
        .content()
        .children()
        .iter()
        .filter_map(|block| Some((block_id(block)?, block)))
        .collect();
    for block in after.content().children() {
        let unchanged = block_id(block)
            .and_then(|id| kept.get(id))
            .is_some_and(|old| *old == block);
        if !unchanged {
            writable(block, held)?;
        }
    }
    Ok(())
}

fn writable(node: &Node, held: &Held<'_>) -> Result<(), String> {
    let attrs = node.attrs();
    let name = schema().node_name(node.kind());
    match name {
        "paragraph" | "table" | "tableRow" => held.properties(name, attrs.get("props"))?,
        "tableCell" => {
            held.properties(name, attrs.get("props"))?;
            if attrs
                .get("span")
                .and_then(Value::as_u64)
                .is_some_and(|span| span > MOST_SPAN)
            {
                return Err(format!("A table cell spans at most {MOST_SPAN} columns."));
            }
        }
        "opaque" | "inlineOpaque" => held.piece(name, attrs.get("raw"))?,
        "image" => match attrs.get("raw") {
            None | Some(Value::Null) => held.picture(attrs.get("asset"))?,
            raw => held.piece(name, raw)?,
        },
        _ => {}
    }
    for mark in node.marks() {
        writable_mark(mark, held)?;
    }
    for child in node.content().children() {
        writable(child, held)?;
    }
    Ok(())
}

fn writable_mark(mark: &Mark, held: &Held<'_>) -> Result<(), String> {
    let attrs = mark.attrs();
    match schema().mark_name(mark.kind()) {
        "run" => held.properties("run", attrs.get("props")),
        "link" if attrs.get("href").and_then(Value::as_str).is_none() => {
            Err("A link needs an address.".to_owned())
        }
        _ => Ok(()),
    }
}
