use std::{collections::HashMap, marker::PhantomData, path::Path, sync::Arc};

use document::{Document, Effect, Format, Input, Restored, Rev, Unreadable};

pub trait Live: Send {
    fn step(&mut self, input: Input) -> Vec<Effect>;
    fn rev(&self) -> Rev;
    fn saved_rev(&self) -> Rev;
    fn seqs(&self) -> HashMap<String, u64>;
    fn is_empty(&self) -> bool;
}

impl<F: Format> Live for Document<F> {
    fn step(&mut self, input: Input) -> Vec<Effect> {
        Self::step(self, input)
    }

    fn rev(&self) -> Rev {
        Self::rev(self)
    }

    fn saved_rev(&self) -> Rev {
        Self::saved_rev(self)
    }

    fn seqs(&self) -> HashMap<String, u64> {
        Self::seqs(self).clone()
    }

    fn is_empty(&self) -> bool {
        Self::is_empty(self)
    }
}

pub trait Opener: Send + Sync {
    fn opens(&self, file: &[u8]) -> bool;

    fn open(&self, file: Vec<u8>, epoch: u64) -> Result<Box<dyn Live>, Unreadable>;

    fn restore(
        &self,
        original: Vec<u8>,
        restored: Restored,
        records: &[Vec<u8>],
    ) -> Result<Box<dyn Live>, Unreadable>;
}

struct FormatOpener<F>(PhantomData<fn() -> F>);

impl<F: Format> Opener for FormatOpener<F> {
    fn opens(&self, file: &[u8]) -> bool {
        F::opens(file).is_ok()
    }

    fn open(&self, file: Vec<u8>, epoch: u64) -> Result<Box<dyn Live>, Unreadable> {
        let document = Document::<F>::open(file, epoch)?;
        Ok(Box::new(document))
    }

    fn restore(
        &self,
        original: Vec<u8>,
        restored: Restored,
        records: &[Vec<u8>],
    ) -> Result<Box<dyn Live>, Unreadable> {
        let document = Document::<F>::restore(original, restored, records)?;
        Ok(Box::new(document))
    }
}

pub fn format<F: Format>() -> Box<dyn Opener> {
    Box::new(FormatOpener::<F>(PhantomData))
}

#[derive(Clone, Default)]
pub struct Formats {
    by_extension: HashMap<String, Arc<dyn Opener>>,
}

impl Formats {
    #[must_use]
    pub fn with(mut self, extension: &str, opener: Box<dyn Opener>) -> Self {
        self.by_extension
            .insert(extension.to_ascii_lowercase(), Arc::from(opener));
        self
    }

    pub(crate) fn of(&self, path: &Path) -> Option<Arc<dyn Opener>> {
        let extension = path.extension()?.to_str()?.to_ascii_lowercase();
        self.by_extension.get(&extension).cloned()
    }

    #[must_use]
    pub fn opens(&self, path: &Path, file: &[u8]) -> bool {
        self.of(path).is_some_and(|opener| opener.opens(file))
    }
}
