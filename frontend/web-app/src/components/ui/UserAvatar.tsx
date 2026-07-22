import './UserAvatar.css';

interface UserAvatarProps {
  avatarUrl?: string;
  firstName?: string;
  className?: string;
}

export default function UserAvatar({ avatarUrl, firstName, className = '' }: UserAvatarProps) {
  const initial = firstName?.charAt(0)?.toUpperCase() || '?';

  return (
    <span className={`user-avatar ${className}`.trim()} aria-hidden="true">
      <span className="user-avatar__fallback">{initial}</span>
      {avatarUrl && (
        <img
          className="user-avatar__image"
          src={avatarUrl}
          alt=""
          onLoad={(event) => {
            event.currentTarget.hidden = false;
          }}
          onError={(event) => {
            event.currentTarget.hidden = true;
          }}
        />
      )}
    </span>
  );
}
