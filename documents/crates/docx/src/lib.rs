//! Documents (.docx). The server keeps a text tree of the body and the original XML of each block, so
//! a save makes new XML only for blocks that changed, and each other byte stays.

mod apply;
mod blank;
mod model;
mod ops;
mod place;
mod read;
mod styles;
mod writable;
mod write;

use document::{Answer, Assets, Format, Guide, Ordered, Refused, Snapshot, Unreadable, Unwritable};
use serde_json::{Value, json};

pub use blank::new_file;
pub use model::{Change, Docx, Model, Query};
pub use ops::Op;

impl Format for Docx {
    type Model = Model;
    type Change = Change;
    type Query = Query;

    fn read(file: &[u8], assets: &mut Assets) -> Result<Model, Unreadable> {
        read::read(file, assets)
    }

    fn snapshot(model: &Model) -> Snapshot {
        Snapshot(json!({
            "doc": model.doc.to_json(model::schema()),
            "view": model.view,
        }))
    }

    /// A view sends steps and an agent sends ops. Pieces of XML and the blocks that they make come
    /// only with the merge of a write of another program, which the server makes itself.
    fn sendable(change: &Change) -> Result<(), String> {
        match change {
            Change::Steps { keep, sources, .. } if keep.is_empty() && sources.is_empty() => Ok(()),
            Change::Ops { .. } => Ok(()),
            Change::Steps { .. } | Change::Merge { .. } => {
                Err("Only the server merges a write of another program.".to_owned())
            }
        }
    }

    fn apply(
        model: &mut Model,
        change: Change,
        since: &[Ordered<Change>],
        assets: &mut Assets,
    ) -> Result<Change, Refused> {
        apply::apply(model, change, since, assets)
    }

    fn compare(from: &Model, to: &Model) -> Vec<Change> {
        apply::compare(from, to)
    }

    fn write(model: &Model, file: &[u8], assets: &Assets) -> Result<Vec<u8>, Unwritable> {
        write::write(model, file, assets)
    }

    fn query(model: &Model, query: Query) -> Answer {
        Answer(ops::answer(model, &query))
    }

    fn guide() -> Guide {
        ops::guide()
    }

    fn map_place(place: &mut Value, change: &Change) {
        place::map(place, change);
    }

    fn describe_place(model: &Model, place: &Value) -> Value {
        place::describe(&model.doc, place)
    }
}

#[cfg(test)]
mod tests;
