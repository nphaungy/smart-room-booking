"""Periodic work that no request triggers: time passing and rooms reopening."""
from .checkin_service import mark_no_shows
from .waitlist_service import expire_entries, promote_quietly, rooms_with_waiting


def run_sweep(conn) -> None:
    mark_no_shows(conn)
    expire_entries(conn)
    # Every room with a queue, not only those with a new no-show: this also picks up closures
    # that were removed, rooms that were reactivated, and promotions that failed earlier.
    for room_id in rooms_with_waiting(conn):
        promote_quietly(conn, room_id)
