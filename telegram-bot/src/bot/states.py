"""FSM-состояния диалога с партнёром."""


class State:
    """FSM states as string constants."""

    IDLE = "IDLE"
    WAIT_FOR_CONTACT = "WAIT_FOR_CONTACT"
    WAIT_FOR_LINK = "WAIT_FOR_LINK"
    WAIT_FOR_PROMO_DETAILS = "WAIT_FOR_PROMO_DETAILS"
    WAIT_FOR_REVISION = "WAIT_FOR_REVISION"
