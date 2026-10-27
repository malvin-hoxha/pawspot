import { BrowserRouter, Routes, Route } from 'react-router';
import { GuestOnlyRoute } from './features/auth/components/GuestOnlyRoute'
import { LoginPage } from './pages/LoginPage';
import { HomePage } from './pages/HomePage';
import { ProtectedRoute } from './features/auth/components/ProtectedRoute';
import { CreatePostPage } from './pages/CreatePostPage';

import { Header } from './components/layout/Header'
import { PostsPage } from './pages/PostsPage';
import { PostDetailsPage } from './pages/PostDetailsPage'

import { MyPostsPage } from './pages/MyPostsPage';
import { EditPostPage } from './pages/EditPostPage';
import { RegisterPage } from './pages/RegisterPage';
import { AccountPage } from './pages/AccountPage';


function App() {

  return (
    <BrowserRouter>
      <Header />

      <main className="p-6">
        <Routes>
          <Route path="/" element={<HomePage />} />

          <Route element={<GuestOnlyRoute />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>

          <Route path="/posts" element={<PostsPage />} />
          <Route path="/posts/:id" element={<PostDetailsPage />} />
          <Route path="*" element={<h1>404 — Page Not Found</h1>} />

          <Route element={<ProtectedRoute />}>
            <Route path="/posts/create" element={<CreatePostPage />} />
            <Route path="/posts/my" element={<MyPostsPage />} />
            <Route path="/posts/:id/edit" element={<EditPostPage />} />
            <Route path="/account" element={<AccountPage />} />
          </Route>
        </Routes>
      </main>
    </BrowserRouter>
  )
}

export default App
