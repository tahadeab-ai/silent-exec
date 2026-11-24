//! Subprocess execution helper ensuring background/CLI commands never spawn a console window on Windows.

#[cfg(windows)]
use std::os::windows::process::CommandExt;

/// Creates a `std::process::Command` configured with `CREATE_NO_WINDOW` (0x08000000)
/// on Windows so no black console window ever flashes.
pub fn hidden_command(program: &str) -> std::process::Command {
    let mut cmd = std::process::Command::new(program);
    #[cfg(windows)]
    {
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    cmd
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hidden_command_instantiation() {
        let cmd = hidden_command("curl");
        assert_eq!(cmd.get_program(), "curl");
    }
}
