pub(crate) fn hex(bytes: &[u8]) -> String {
    const DIGITS: &[u8; 16] = b"0123456789abcdef";
    let mut text = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        text.push(char::from(DIGITS[usize::from(byte >> 4)]));
        text.push(char::from(DIGITS[usize::from(byte & 0xf)]));
    }
    text
}

pub(crate) fn hash_from_hex(text: &str) -> Option<[u8; 32]> {
    let (pairs, []) = text.as_bytes().as_chunks::<2>() else {
        return None;
    };
    let pairs: &[[u8; 2]; 32] = pairs.try_into().ok()?;
    let mut hash = [0; 32];
    for (byte, [high, low]) in hash.iter_mut().zip(pairs) {
        let digit = |digit: u8| char::from(digit).to_digit(16);
        *byte = u8::try_from(digit(*high)? << 4 | digit(*low)?).ok()?;
    }
    Some(hash)
}
