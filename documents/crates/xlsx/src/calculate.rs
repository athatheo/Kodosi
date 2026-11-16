use std::time::Duration;

use formualizer::{
    DateSystem,
    common::{ExcelError, ExcelErrorKind, LiteralValue, RangeAddress},
    eval::engine::{EvaluationProgress, EvaluationStop},
    workbook::{NamedRangeScope, Workbook, WorkbookConfig},
};

use crate::{
    formula::parse_range,
    model::{Model, Value},
};

const CALCULATION_TIME: Duration = Duration::from_secs(5);

/// A formula that the engine cannot work out, such as one with a function that it does not know
/// or one in a loop, keeps its value. So does each formula of a workbook that the engine cannot
/// work out in time, so that a heavy or a hostile file does not hold up its document.
pub(crate) fn calculated(model: &Model) -> Model {
    let mut current = model.clone();
    let formulas = model
        .sheets
        .iter()
        .any(|sheet| sheet.cells.values().any(|cell| cell.formula.is_some()));
    if !formulas {
        return current;
    }
    let Some(book) = workbook(model) else {
        return current;
    };
    let dates = date_system(model);
    for sheet in &mut current.sheets {
        for (&(row, column), cell) in &mut sheet.cells {
            if cell.formula.is_some()
                && let Some(value) = book
                    .get_value(&sheet.name, row + 1, column + 1)
                    .and_then(|value| value_of(value, dates))
            {
                cell.value = Some(value);
            }
        }
    }
    current
}

const fn date_system(model: &Model) -> DateSystem {
    if model.dates_from_1904 {
        DateSystem::Excel1904
    } else {
        DateSystem::Excel1900
    }
}

fn workbook(model: &Model) -> Option<Workbook> {
    let mut config = WorkbookConfig::ephemeral();
    config.eval = config.eval.with_date_system(date_system(model));
    let mut book = Workbook::new_with_config(config);
    for sheet in &model.sheets {
        book.add_sheet(&sheet.name).ok()?;
    }
    for name in &model.names {
        if let Some(address) = address(&name.text) {
            let scope = if name.sheet.is_some() {
                NamedRangeScope::Sheet
            } else {
                NamedRangeScope::Workbook
            };
            drop(book.define_named_range(&name.name, &address, scope));
        }
    }
    for sheet in &model.sheets {
        for (&(row, column), cell) in &sheet.cells {
            let (row, column) = (row + 1, column + 1);
            match (&cell.formula, &cell.value) {
                (Some(formula), _) => book.set_formula(&sheet.name, row, column, formula).ok()?,
                (None, Some(value)) => book
                    .set_value(&sheet.name, row, column, literal(value))
                    .ok()?,
                (None, None) => {}
            }
        }
    }
    let progress = book
        .evaluate_all_until(EvaluationStop::after(CALCULATION_TIME))
        .ok()?;
    matches!(progress, EvaluationProgress::Complete(_)).then_some(book)
}

fn address(text: &str) -> Option<RangeAddress> {
    let (sheet, cells) = text.trim_start_matches('=').rsplit_once('!')?;
    let sheet = sheet
        .strip_circumfix('\'', '\'')
        .map_or_else(|| sheet.to_owned(), |quoted| quoted.replace("''", "'"));
    let [top, left, bottom, right] = parse_range(&cells.replace('$', ""))?;
    RangeAddress::new(sheet, top + 1, left + 1, bottom + 1, right + 1).ok()
}

fn literal(value: &Value) -> LiteralValue {
    match value {
        Value::Number(number) => LiteralValue::Number(*number),
        Value::Bool(on) => LiteralValue::Boolean(*on),
        Value::Text(text) => LiteralValue::Text(text.clone()),
        Value::Error { error } => LiteralValue::Error(ExcelError::from_error_string(error)),
    }
}

#[expect(
    clippy::cast_precision_loss,
    reason = "a sheet keeps each number as a double"
)]
fn value_of(value: LiteralValue, dates: DateSystem) -> Option<Value> {
    Some(match value.finite_or_num_error() {
        LiteralValue::Int(number) => Value::Number(number as f64),
        LiteralValue::Number(number) => Value::Number(number),
        LiteralValue::Text(text) => Value::Text(text),
        LiteralValue::Boolean(on) => Value::Bool(on),
        LiteralValue::Empty => Value::Number(0.0),
        LiteralValue::Array(rows) => {
            return rows
                .into_iter()
                .next()?
                .into_iter()
                .next()
                .and_then(|value| value_of(value, dates));
        }
        LiteralValue::Error(error)
            if matches!(
                error.kind,
                ExcelErrorKind::Name | ExcelErrorKind::NImpl | ExcelErrorKind::Circ
            ) =>
        {
            return None;
        }
        LiteralValue::Error(error) => Value::Error {
            error: error.kind.to_string(),
        },
        LiteralValue::Pending => return None,
        temporal => Value::Number(temporal.as_serial_number_for(dates)?),
    })
}
